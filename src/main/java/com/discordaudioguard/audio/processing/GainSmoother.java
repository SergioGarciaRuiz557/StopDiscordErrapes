package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;

/**
 * Exponential gain smoother operating in the logarithmic decibel domain.
 *
 * <p>A more negative target gain means greater attenuation and uses attack; recovery
 * toward zero uses release. This distinction prevents peaks from passing through the
 * compressor while reducing pumping as volume recovers.</p>
 */
public final class GainSmoother {
    /** Sample rate used to convert milliseconds into coefficients. */
    private final double sampleRate;
    /** Coefficient for increasing attenuation. */
    private double attackCoefficient;
    /** Coefficient for releasing attenuation. */
    private double releaseCoefficient;
    /** Current smoothed gain in dB, normally zero or negative. */
    private double gainDb;

    /**
     * Creates a smoother with initial unity gain (0 dB).
     *
     * @param sampleRate samples per second
     * @param attackMs attack time in milliseconds
     * @param releaseMs release time in milliseconds
     */
    public GainSmoother(double sampleRate, double attackMs, double releaseMs) {
        this.sampleRate = sampleRate;
        configure(attackMs, releaseMs);
    }

    /**
     * Updates timing while preserving accumulated gain to avoid discontinuities.
     *
     * @param attackMs attack time in milliseconds
     * @param releaseMs release time in milliseconds
     */
    public void configure(double attackMs, double releaseMs) {
        attackCoefficient = DecibelUtils.timeCoefficient(attackMs, sampleRate);
        releaseCoefficient = DecibelUtils.timeCoefficient(releaseMs, sampleRate);
    }

    /**
     * Moves the gain one sample closer to the requested target.
     *
     * @param targetGainDb target gain in dB
     * @return smoothed gain to apply to this sample
     */
    public double process(double targetGainDb) {
        double coefficient = targetGainDb < gainDb ? attackCoefficient : releaseCoefficient;
        gainDb = coefficient * gainDb + (1.0 - coefficient) * targetGainDb;
        return gainDb;
    }

    /** Removes any retained attenuation. */
    public void reset() { gainDb = 0.0; }
}
