package com.discordaudioguard.audio.processing;

import java.util.Arrays;

/** Fixed-capacity stereo delay used by the limiter; resizing never occurs on the audio thread. */
public final class LookaheadBuffer {
    private final float[] left;
    private final float[] right;
    private int delayFrames;
    private int writeIndex;

    public LookaheadBuffer(int maximumFrames) {
        left = new float[maximumFrames + 1];
        right = new float[maximumFrames + 1];
    }

    public void setDelayFrames(int frames) {
        int value = Math.max(0, Math.min(frames, left.length - 1));
        delayFrames = value;
    }

    public void push(float leftSample, float rightSample) {
        left[writeIndex] = leftSample;
        right[writeIndex] = rightSample;
    }

    public float delayedLeft() { return left[readIndex()]; }
    public float delayedRight() { return right[readIndex()]; }
    public int capacity() { return left.length; }
    public int delayFrames() { return delayFrames; }
    public int writeIndex() { return writeIndex; }

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

    public void advance() {
        writeIndex++;
        if (writeIndex == left.length) writeIndex = 0;
    }

    private int readIndex() {
        int index = writeIndex - delayFrames;
        return index < 0 ? index + left.length : index;
    }

    public void reset() {
        Arrays.fill(left, 0.0f);
        Arrays.fill(right, 0.0f);
        writeIndex = 0;
    }
}
