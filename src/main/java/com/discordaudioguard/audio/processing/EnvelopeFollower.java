package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;

public final class EnvelopeFollower {
    private final double sampleRate;
    private double attackCoefficient;
    private double releaseCoefficient;
    private double envelope;

    public EnvelopeFollower(double sampleRate, double attackMs, double releaseMs) {
        this.sampleRate = sampleRate;
        configure(attackMs, releaseMs);
    }

    public void configure(double attackMs, double releaseMs) {
        attackCoefficient = DecibelUtils.timeCoefficient(attackMs, sampleRate);
        releaseCoefficient = DecibelUtils.timeCoefficient(releaseMs, sampleRate);
    }

    public double process(double level) {
        double coefficient = level > envelope ? attackCoefficient : releaseCoefficient;
        envelope = coefficient * envelope + (1.0 - coefficient) * level;
        return envelope;
    }

    public void reset() { envelope = 0.0; }
}
