package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;

/**
 * Exponential envelope follower that estimates a signal's instantaneous level.
 *
 * <p>It uses different constants for rising and falling levels: attack reacts quickly
 * to a new peak, while release prevents the estimate from dropping abruptly. State is
 * preserved sample by sample and used as the compressor detector.</p>
 */
public final class EnvelopeFollower {
    /** Sample rate needed to convert times into per-sample coefficients. */
    private final double sampleRate;
    /** Coefficient applied while the level rises. */
    private double attackCoefficient;
    /** Coefficient applied while the level falls. */
    private double releaseCoefficient;
    /** Most recent smoothed level. */
    private double envelope;

    /**
     * Creates a detector initialized to silence with the specified constants.
     *
     * @param sampleRate samples processed per second
     * @param attackMs rising time constant in milliseconds
     * @param releaseMs falling time constant in milliseconds
     */
    public EnvelopeFollower(double sampleRate, double attackMs, double releaseMs) {
        this.sampleRate = sampleRate;
        configure(attackMs, releaseMs);
    }

    /**
     * Recomputes time constants without clearing the current level, avoiding jumps.
     *
     * @param attackMs attack time in milliseconds
     * @param releaseMs release time in milliseconds
     */
    public void configure(double attackMs, double releaseMs) {
        attackCoefficient = DecibelUtils.timeCoefficient(attackMs, sampleRate);
        releaseCoefficient = DecibelUtils.timeCoefficient(releaseMs, sampleRate);
    }

    /**
     * Feeds one level sample into the one-pole filter.
     *
     * @param level non-negative linear magnitude detected in the current frame
     * @return new smoothed envelope
     */
    public double process(double level) {
        double coefficient = level > envelope ? attackCoefficient : releaseCoefficient;
        envelope = coefficient * envelope + (1.0 - coefficient) * level;
        return envelope;
    }

    /** Resets the detector to silence. */
    public void reset() { envelope = 0.0; }
}
