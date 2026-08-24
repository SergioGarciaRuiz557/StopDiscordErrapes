package com.discordaudioguard.audio.engine;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stereo PCM ring buffer that decouples capture and playback while compensating drift.
 *
 * <p>Two physical devices have slightly different clocks. Even when both declare
 * 48 kHz, sustained capture may produce a few more or fewer samples than playback;
 * without correction, any fixed buffer would eventually overflow or empty. This class
 * maintains a target occupancy and gently adapts reader speed between 0.995x and 1.005x.</p>
 *
 * <p>Playback is resampled through linear interpolation between two frames.
 * {@code readPhase} preserves the fractional part across blocks, avoiding jumps.
 * Producer and consumer coordinate with separate conditions for data and space;
 * {@link #close()} wakes both so they can terminate without a permanent block.</p>
 *
 * <p>The class has package visibility because it is an engine implementation detail.</p>
 */
final class AdaptivePcmBuffer {
    /** Lowest permitted reader speed: -0.5%. */
    private static final double MIN_RATE = 0.995;
    /** Highest permitted reader speed: +0.5%. */
    private static final double MAX_RATE = 1.005;
    /** Proportional gain that converts occupancy error into clock correction. */
    private static final double CONTROLLER_GAIN = 0.01;

    /** Left-sample ring whose length is the frame capacity. */
    private final short[] left;
    /** Right-sample ring parallel to {@link #left}. */
    private final short[] right;
    /** Lock protecting indices, occupancy, phase, and closure. */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition awaited by the consumer when frames are unavailable. */
    private final Condition dataAvailable = lock.newCondition();
    /** Condition awaited by the producer when the ring is full. */
    private final Condition spaceAvailable = lock.newCondition();
    /** Position of the next whole frame available to the reader. */
    private int readIndex;
    /** Position where the producer inserts the next frame. */
    private int writeIndex;
    /** Number of valid whole frames currently stored. */
    private int sizeFrames;
    /** Frame fraction accumulated by adaptive resampling. */
    private double readPhase;
    /** Latest effective speed, published for diagnostics without locking. */
    private volatile double playbackRate = 1.0;
    /** Terminal signal that prevents new writes and unblocks waiters. */
    private boolean closed;

    /**
     * Allocates both channels with a fixed capacity that never changes in real time.
     *
     * @param capacityFrames maximum number of storable stereo frames
     * @throws IllegalArgumentException if there is insufficient room for safe interpolation
     */
    AdaptivePcmBuffer(int capacityFrames) {
        if (capacityFrames < 4) throw new IllegalArgumentException("El búfer adaptativo necesita al menos cuatro frames");
        left = new short[capacityFrames];
        right = new short[capacityFrames];
    }

    /**
     * Decodes and inserts a PCM block, waiting for space when necessary.
     *
     * @param source interleaved stereo little-endian PCM16
     * @param byteCount valid bytes, necessarily a multiple of four
     * @return {@code false} if the buffer closed before accepting the block
     * @throws InterruptedException if the producer is interrupted while waiting
     */
    boolean putPcm16LittleEndian(byte[] source, int byteCount) throws InterruptedException {
        if (byteCount < 0 || byteCount > source.length || byteCount % 4 != 0) {
            throw new IllegalArgumentException("El bloque PCM debe contener frames estéreo completos");
        }
        int frames = byteCount / 4;
        if (frames > left.length) throw new IllegalArgumentException("El bloque PCM supera la capacidad del búfer");
        lock.lockInterruptibly();
        try {
            while (left.length - sizeFrames < frames && !closed) spaceAvailable.await();
            if (closed) return false;
            for (int frame = 0, offset = 0; frame < frames; frame++, offset += 4) {
                left[writeIndex] = decodeShort(source, offset);
                right[writeIndex] = decodeShort(source, offset + 2);
                writeIndex = increment(writeIndex);
            }
            sizeFrames += frames;
            dataAvailable.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits for a sufficient initial reserve for stable playback.
     *
     * @param minimumFrames desired minimum occupancy
     * @return {@code true} if reached; {@code false} if the buffer closed first
     * @throws InterruptedException if the wait is interrupted
     */
    boolean awaitFrames(int minimumFrames) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (sizeFrames < minimumFrames && !closed) dataAvailable.await();
            return sizeFrames >= minimumFrames;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Produces a PCM block at an adaptive rate through linear interpolation.
     *
     * <p>The proportional controller compares occupancy with its target. Before output
     * is generated, rate is also constrained by the frames actually available, leaving
     * two frames of margin so every interpolation has a neighboring sample.</p>
     *
     * @param destination stereo little-endian PCM16 destination
     * @param outputFrames exact number of frames to generate
     * @param targetFrames occupancy that represents nominal speed
     * @return encoded bytes, or -1 if the closed buffer no longer contains the minimum
     * @throws InterruptedException if the consumer is interrupted while waiting
     */
    int readPcm16LittleEndian(byte[] destination, int outputFrames, int targetFrames) throws InterruptedException {
        int byteCount = outputFrames * 4;
        if (outputFrames < 1 || targetFrames < outputFrames || byteCount > destination.length) {
            throw new IllegalArgumentException("Lectura PCM adaptativa no válida");
        }
        lock.lockInterruptibly();
        try {
            while (sizeFrames < outputFrames + 2 && !closed) dataAvailable.await();
            if (sizeFrames < outputFrames + 2) return -1;

            // Positive error consumes slightly faster; negative error consumes more slowly.
            double error = (sizeFrames - targetFrames) / (double) targetFrames;
            double rate = clamp(1.0 + error * CONTROLLER_GAIN, MIN_RATE, MAX_RATE);
            if (outputFrames > 1) {
                double maximumSafeRate = (sizeFrames - readPhase - 2.0) / (outputFrames - 1.0);
                rate = Math.min(rate, maximumSafeRate);
            }
            rate = Math.max(0.5, rate);

            double position = readPhase;
            for (int frame = 0, offset = 0; frame < outputFrames; frame++, offset += 4) {
                int wholeFrames = (int) position;
                double fraction = position - wholeFrames;
                int first = indexFromRead(wholeFrames);
                int second = increment(first);
                int leftValue = interpolate(left[first], left[second], fraction);
                int rightValue = interpolate(right[first], right[second], fraction);
                encodeShort(destination, offset, leftValue);
                encodeShort(destination, offset + 2, rightValue);
                position += rate;
            }

            // Advance only whole frames and preserve the fraction for the next block.
            int consumedFrames = (int) position;
            readPhase = position - consumedFrames;
            readIndex = indexFromRead(consumedFrames);
            sizeFrames -= consumedFrames;
            playbackRate = rate;
            spaceAvailable.signalAll();
            return byteCount;
        } finally {
            lock.unlock();
        }
    }

    /** @return current occupancy in frames, obtained under the lock */
    int bufferedFrames() {
        lock.lock();
        try {
            return sizeFrames;
        } finally {
            lock.unlock();
        }
    }

    /** @return speed used by the latest read; 1.0 represents nominal speed */
    double playbackRate() { return playbackRate; }

    /** Marks the buffer terminal and wakes any blocked producer or consumer. */
    void close() {
        lock.lock();
        try {
            closed = true;
            dataAvailable.signalAll();
            spaceAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Converts a logical offset from the read position into a physical ring index. */
    private int indexFromRead(int offset) { return (readIndex + offset) % left.length; }

    /** Advances one position and wraps to zero at the end of the ring. */
    private int increment(int index) { return index + 1 == left.length ? 0 : index + 1; }

    /** Reconstructs one signed little-endian PCM16 sample. */
    private static short decodeShort(byte[] source, int offset) {
        return (short) ((source[offset] & 0xff) | (source[offset + 1] << 8));
    }

    /** Writes the least-significant 16 bits in little-endian order. */
    private static void encodeShort(byte[] destination, int offset, int value) {
        destination[offset] = (byte) value;
        destination[offset + 1] = (byte) (value >>> 8);
    }

    /** Interpolates and rounds between two adjacent PCM samples. */
    private static int interpolate(short first, short second, double fraction) {
        return (int) Math.round(first + (second - first) * fraction);
    }

    /** Clamps controller correction to the permitted stable interval. */
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
