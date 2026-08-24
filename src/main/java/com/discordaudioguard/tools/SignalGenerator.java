package com.discordaudioguard.tools;

import java.util.Random;

/**
 * Deterministic stereo signal generator for DSP tests and diagnostics.
 *
 * <p>Every destination contains interleaved L/R {@code float} samples and is modified
 * in place. These utilities exercise the pipeline without Discord or physical devices
 * and reproduce failures exactly through explicit seeds and frame positions.</p>
 */
public final class SignalGenerator {
    /** Prevents instantiation of this purely static class. */
    private SignalGenerator() {}

    /**
     * Replaces any previous signal with zeros.
     *
     * @param destination buffer to fill completely with silence
     */
    public static void silence(float[] destination) { java.util.Arrays.fill(destination, 0.0f); }

    /**
     * Generates a mono sine wave duplicated across both channels without block discontinuities.
     *
     * @param destination interleaved stereo destination
     * @param sampleRate sample rate in Hz
     * @param frequency sine-wave frequency in Hz
     * @param amplitude linear peak amplitude
     * @param startFrame absolute first-frame position used to preserve phase
     */
    public static void sine(float[] destination, double sampleRate, double frequency, double amplitude, long startFrame) {
        int frames = destination.length / 2;
        for (int frame = 0, sample = 0; frame < frames; frame++, sample += 2) {
            float value = (float) (Math.sin(2.0 * Math.PI * frequency * (startFrame + frame) / sampleRate) * amplitude);
            destination[sample] = value; destination[sample + 1] = value;
        }
    }

    /**
     * Generates identical uniform noise in the left and right channels.
     *
     * @param destination interleaved stereo destination
     * @param amplitude maximum absolute signal value
     * @param seed seed that makes the sequence repeatable
     */
    public static void noise(float[] destination, double amplitude, long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < destination.length; i += 2) {
            float value = (float) ((random.nextDouble() * 2.0 - 1.0) * amplitude);
            destination[i] = value; destination[i + 1] = value;
        }
    }

    /**
     * Creates a moderate sine wave and inserts a full-scale impulse to test the limiter.
     *
     * @param destination interleaved stereo destination
     * @param sampleRate sample rate in Hz
     * @param peakFrame impulse frame, clamped to the valid buffer range
     * @param leftOnly whether the impulse should affect only the left channel
     */
    public static void sineWithPeak(float[] destination, double sampleRate, int peakFrame, boolean leftOnly) {
        sine(destination, sampleRate, 1_000.0, 0.1, 0);
        int sample = Math.max(0, Math.min(destination.length / 2 - 1, peakFrame)) * 2;
        destination[sample] = 1.0f;
        if (!leftOnly) destination[sample + 1] = 1.0f;
    }

    /**
     * Generates a sine wave whose amplitude interpolates linearly between two endpoints.
     *
     * @param destination interleaved stereo destination
     * @param sampleRate sample rate in Hz
     * @param frequency sine-wave frequency in Hz
     * @param startAmplitude linear amplitude of the first frame
     * @param endAmplitude linear amplitude of the last frame
     */
    public static void risingSine(float[] destination, double sampleRate, double frequency, double startAmplitude, double endAmplitude) {
        int frames = destination.length / 2;
        for (int frame = 0, sample = 0; frame < frames; frame++, sample += 2) {
            double mix = frame / (double) Math.max(1, frames - 1);
            double amplitude = startAmplitude + (endAmplitude - startAmplitude) * mix;
            float value = (float) (Math.sin(2.0 * Math.PI * frequency * frame / sampleRate) * amplitude);
            destination[sample] = value; destination[sample + 1] = value;
        }
    }
}
