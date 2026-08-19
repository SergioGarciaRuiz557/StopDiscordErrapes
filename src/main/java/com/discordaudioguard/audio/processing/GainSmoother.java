package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;

public final class GainSmoother {
    private final double sampleRate;
    private double attackCoefficient;
    private double releaseCoefficient;
    private double gainDb;

    public GainSmoother(double sampleRate, double attackMs, double releaseMs) {
        this.sampleRate = sampleRate;
        configure(attackMs, releaseMs);
    }

    public void configure(double attackMs, double releaseMs) {
        attackCoefficient = DecibelUtils.timeCoefficient(attackMs, sampleRate);
        releaseCoefficient = DecibelUtils.timeCoefficient(releaseMs, sampleRate);
    }

    public double process(double targetGainDb) {
        double coefficient = targetGainDb < gainDb ? attackCoefficient : releaseCoefficient;
        gainDb = coefficient * gainDb + (1.0 - coefficient) * targetGainDb;
        return gainDb;
    }

    public void reset() { gainDb = 0.0; }
}
