package com.discordaudioguard.util;

/** Small mathematical operations shared by the audio pipeline. */
public final class MathUtils {
    /** Prevents instantiation of this purely static class. */
    private MathUtils() {}

    /**
     * Clamps a double-precision value to a closed interval.
     *
     * @param value value to clamp
     * @param minimum inclusive lower bound
     * @param maximum inclusive upper bound
     * @return {@code value} when it lies inside the interval, otherwise the nearest bound
     */
    public static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Single-precision equivalent of {@link #clamp(double, double, double)}.
     *
     * @param value value to clamp
     * @param minimum inclusive lower bound
     * @param maximum inclusive upper bound
     * @return original value or the nearest bound
     */
    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
