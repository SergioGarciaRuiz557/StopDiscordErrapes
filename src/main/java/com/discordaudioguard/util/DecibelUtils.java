package com.discordaudioguard.util;

public final class DecibelUtils {
    public static final double MINIMUM_DB = -120.0;
    private static final double EPSILON = 1.0e-6;

    private DecibelUtils() {}

    public static double amplitudeToDb(double amplitude) {
        if (!Double.isFinite(amplitude) || Math.abs(amplitude) < EPSILON) {
            return MINIMUM_DB;
        }
        return Math.max(MINIMUM_DB, 20.0 * Math.log10(Math.abs(amplitude)));
    }

    public static double dbToLinear(double decibels) {
        if (!Double.isFinite(decibels)) {
            return decibels > 0 ? 1.0 : 0.0;
        }
        return Math.pow(10.0, decibels / 20.0);
    }

    public static double timeCoefficient(double milliseconds, double sampleRate) {
        if (milliseconds <= 0.0) return 0.0;
        return Math.exp(-1.0 / (milliseconds * 0.001 * sampleRate));
    }
}
