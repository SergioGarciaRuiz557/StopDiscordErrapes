package com.discordaudioguard.util;

/**
 * Common conversions and coefficients for digital audio processing.
 *
 * <p>Linear amplitude uses 1.0 as full scale (0 dBFS). To avoid taking the logarithm
 * of zero, extremely small values are represented by a conventional floor of
 * {@value #MINIMUM_DB} dB.</p>
 */
public final class DecibelUtils {
    /** Numerical floor used to represent practical silence. */
    public static final double MINIMUM_DB = -120.0;
    /** Amplitudes below this value are treated as silence. */
    private static final double EPSILON = 1.0e-6;

    /** Prevents instantiation of this purely static class. */
    private DecibelUtils() {}

    /**
     * Converts linear amplitude to full-scale decibels.
     *
     * @param amplitude amplitude whose sign is ignored
     * @return {@code 20*log10(|amplitude|)} bounded by {@link #MINIMUM_DB}
     */
    public static double amplitudeToDb(double amplitude) {
        if (!Double.isFinite(amplitude) || Math.abs(amplitude) < EPSILON) {
            return MINIMUM_DB;
        }
        return Math.max(MINIMUM_DB, 20.0 * Math.log10(Math.abs(amplitude)));
    }

    /**
     * Converts gain expressed in decibels to a linear factor.
     *
     * @param decibels gain or level in dB
     * @return factor {@code 10^(dB/20)}; infinities are normalized to zero or one
     */
    public static double dbToLinear(double decibels) {
        if (!Double.isFinite(decibels)) {
            return decibels > 0 ? 1.0 : 0.0;
        }
        return Math.pow(10.0, decibels / 20.0);
    }

    /**
     * Computes the decay coefficient of a one-pole exponential filter.
     *
     * @param milliseconds time constant in milliseconds
     * @param sampleRate samples processed per second
     * @return coefficient between zero and one; zero for instantaneous response
     */
    public static double timeCoefficient(double milliseconds, double sampleRate) {
        if (milliseconds <= 0.0) return 0.0;
        return Math.exp(-1.0 / (milliseconds * 0.001 * sampleRate));
    }
}
