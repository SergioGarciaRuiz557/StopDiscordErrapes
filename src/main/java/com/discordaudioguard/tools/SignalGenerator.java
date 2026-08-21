package com.discordaudioguard.tools;

import java.util.Random;

/** Development signal source for exercising DSP without Discord or physical audio devices. */
public final class SignalGenerator {
    private SignalGenerator() {}

    public static void silence(float[] destination) { java.util.Arrays.fill(destination, 0.0f); }

    public static void sine(float[] destination, double sampleRate, double frequency, double amplitude, long startFrame) {
        int frames = destination.length / 2;
        for (int frame = 0, sample = 0; frame < frames; frame++, sample += 2) {
            float value = (float) (Math.sin(2.0 * Math.PI * frequency * (startFrame + frame) / sampleRate) * amplitude);
            destination[sample] = value; destination[sample + 1] = value;
        }
    }

    public static void noise(float[] destination, double amplitude, long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < destination.length; i += 2) {
            float value = (float) ((random.nextDouble() * 2.0 - 1.0) * amplitude);
            destination[i] = value; destination[i + 1] = value;
        }
    }

    public static void sineWithPeak(float[] destination, double sampleRate, int peakFrame, boolean leftOnly) {
        sine(destination, sampleRate, 1_000.0, 0.1, 0);
        int sample = Math.max(0, Math.min(destination.length / 2 - 1, peakFrame)) * 2;
        destination[sample] = 1.0f;
        if (!leftOnly) destination[sample + 1] = 1.0f;
    }

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
