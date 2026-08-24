package com.discordaudioguard.audio.processing;

import java.util.Arrays;

/**
 * Fixed-capacity stereo delay line used by the lookahead limiter.
 *
 * <p>The signal is written at the current position and read several frames behind.
 * Meanwhile, {@link #peakInWindow()} inspects from the delayed sample through the newly
 * written one, allowing gain to be calculated before the peak reaches the output.
 * Arrays are allocated once and never resized on the audio path.</p>
 */
public final class LookaheadBuffer {
    /** Left-channel ring. */
    private final float[] left;
    /** Right-channel ring. */
    private final float[] right;
    /** Configured effective delay in frames. */
    private int delayFrames;
    /** Position at which the current frame is written. */
    private int writeIndex;

    /**
     * Allocates the delay line for its entire lifetime.
     *
     * @param maximumFrames maximum configurable delay; one extra frame is reserved
     */
    public LookaheadBuffer(int maximumFrames) {
        left = new float[maximumFrames + 1];
        right = new float[maximumFrames + 1];
    }

    /**
     * Selects the delay, silently clamping it to available capacity.
     *
     * @param frames requested delay in frames
     */
    public void setDelayFrames(int frames) {
        int value = Math.max(0, Math.min(frames, left.length - 1));
        delayFrames = value;
    }

    /**
     * Writes the current frame without advancing the ring yet.
     *
     * @param leftSample normalized left sample
     * @param rightSample normalized right sample
     */
    public void push(float leftSample, float rightSample) {
        left[writeIndex] = leftSample;
        right[writeIndex] = rightSample;
    }

    /**
     * Reads the left channel of the frame leaving the delay line.
     *
     * @return sample at the configured distance
     */
    public float delayedLeft() { return left[readIndex()]; }

    /**
     * Reads the right channel of the frame leaving the delay line.
     *
     * @return sample at the configured distance
     */
    public float delayedRight() { return right[readIndex()]; }

    /**
     * Returns the fixed physical capacity.
     *
     * @return reserved frames, including the auxiliary frame
     */
    public int capacity() { return left.length; }

    /**
     * Returns the distance between writing and reading.
     *
     * @return effective delay in frames
     */
    public int delayFrames() { return delayFrames; }

    /**
     * Exposes the cursor for testing circular behavior.
     *
     * @return physical index of the current frame
     */
    public int writeIndex() { return writeIndex; }

    /**
     * Finds the greatest magnitude across both channels in the full lookahead window.
     *
     * @return absolute linear peak between the delayed output and current frame
     */
    public float peakInWindow() {
        float peak = 0.0f;
        int index = readIndex();
        for (int i = 0; i <= delayFrames; i++) {
            peak = Math.max(peak, Math.max(Math.abs(left[index]), Math.abs(right[index])));
            index++;
            if (index == left.length) index = 0;
        }
        return peak;
    }

    /** Commits the current frame and advances the cursor. */
    public void advance() {
        writeIndex++;
        if (writeIndex == left.length) writeIndex = 0;
    }

    /** Computes the circular position {@code delayFrames} behind the cursor. */
    private int readIndex() {
        int index = writeIndex - delayFrames;
        return index < 0 ? index + left.length : index;
    }

    /** Clears retained audio and returns the cursor to the origin. */
    public void reset() {
        Arrays.fill(left, 0.0f);
        Arrays.fill(right, 0.0f);
        writeIndex = 0;
    }
}
