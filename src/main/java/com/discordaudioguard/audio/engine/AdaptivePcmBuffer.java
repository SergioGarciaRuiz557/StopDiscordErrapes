package com.discordaudioguard.audio.engine;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stereo PCM ring buffer with gentle clock-drift compensation.
 * The reader changes its rate by at most 0.5% using linear interpolation so
 * independent capture and playback clocks do not eventually under/overflow.
 */
final class AdaptivePcmBuffer {
    private static final double MIN_RATE = 0.995;
    private static final double MAX_RATE = 1.005;
    private static final double CONTROLLER_GAIN = 0.01;

    private final short[] left;
    private final short[] right;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition dataAvailable = lock.newCondition();
    private final Condition spaceAvailable = lock.newCondition();
    private int readIndex;
    private int writeIndex;
    private int sizeFrames;
    private double readPhase;
    private volatile double playbackRate = 1.0;
    private boolean closed;

    AdaptivePcmBuffer(int capacityFrames) {
        if (capacityFrames < 4) throw new IllegalArgumentException("El búfer adaptativo necesita al menos cuatro frames");
        left = new short[capacityFrames];
        right = new short[capacityFrames];
    }

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

    boolean awaitFrames(int minimumFrames) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (sizeFrames < minimumFrames && !closed) dataAvailable.await();
            return sizeFrames >= minimumFrames;
        } finally {
            lock.unlock();
        }
    }

    /** Returns the encoded byte count, or -1 after the buffer has closed and drained. */
    int readPcm16LittleEndian(byte[] destination, int outputFrames, int targetFrames) throws InterruptedException {
        int byteCount = outputFrames * 4;
        if (outputFrames < 1 || targetFrames < outputFrames || byteCount > destination.length) {
            throw new IllegalArgumentException("Lectura PCM adaptativa no válida");
        }
        lock.lockInterruptibly();
        try {
            while (sizeFrames < outputFrames + 2 && !closed) dataAvailable.await();
            if (sizeFrames < outputFrames + 2) return -1;

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

    int bufferedFrames() {
        lock.lock();
        try {
            return sizeFrames;
        } finally {
            lock.unlock();
        }
    }

    double playbackRate() { return playbackRate; }

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

    private int indexFromRead(int offset) { return (readIndex + offset) % left.length; }
    private int increment(int index) { return index + 1 == left.length ? 0 : index + 1; }

    private static short decodeShort(byte[] source, int offset) {
        return (short) ((source[offset] & 0xff) | (source[offset + 1] << 8));
    }

    private static void encodeShort(byte[] destination, int offset, int value) {
        destination[offset] = (byte) value;
        destination[offset + 1] = (byte) (value >>> 8);
    }

    private static int interpolate(short first, short second, double fraction) {
        return (int) Math.round(first + (second - first) * fraction);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
