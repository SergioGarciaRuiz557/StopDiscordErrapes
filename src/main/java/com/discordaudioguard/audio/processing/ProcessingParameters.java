package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.MathUtils;

/**
 * Immutable, validated set of every control in the DSP chain.
 *
 * <p>Units are part of the contract: levels and gains use dBFS/dB, times use
 * milliseconds, and ratio is dimensionless. Nested records validate their ranges at
 * construction, so every existing instance is safe to deliver directly to the audio thread.</p>
 *
 * @param compressor compression controls
 * @param limiter peak-limiting controls
 * @param bypass whether the direct path should be heard instead of DSP
 * @param maximumOutputGainDb final gain, restricted to non-positive values
 */
public record ProcessingParameters(CompressorSettings compressor, LimiterSettings limiter,
                                   boolean bypass, double maximumOutputGainDb) {
    /** Balanced initial setting for voice and protection against abrupt peaks. */
    public static final ProcessingParameters DEFAULT = new ProcessingParameters(
            new CompressorSettings(true, -18.0, 6.0, 3.0, 250.0, 6.0, 0.0),
            new LimiterSettings(true, -2.0, 5.0, 150.0), false, 0.0);

    /** Validates required components and the final-gain limit. */
    public ProcessingParameters {
        if (compressor == null || limiter == null) throw new IllegalArgumentException("Los parámetros DSP no pueden ser nulos");
        maximumOutputGainDb = range("Ganancia máxima", maximumOutputGainDb, -30, 0);
    }

    /**
     * States that the instance satisfies its invariants. This is useful at generic
     * boundaries even though actual validation already occurred in the compact constructor.
     *
     * @return this same immutable instance
     */
    public ProcessingParameters validated() { return this; }

    /**
     * Enables or disables the global direct path.
     *
     * @param value new bypass state
     * @return copy with the specified bypass state
     */
    public ProcessingParameters withBypass(boolean value) { return new ProcessingParameters(compressor, limiter, value, maximumOutputGainDb); }

    /**
     * Replaces the first-stage configuration.
     *
     * @param value new compressor controls
     * @return copy with the specified controls
     */
    public ProcessingParameters withCompressor(CompressorSettings value) { return new ProcessingParameters(value, limiter, bypass, maximumOutputGainDb); }

    /**
     * Replaces the second-stage configuration.
     *
     * @param value new limiter controls
     * @return copy with the specified controls
     */
    public ProcessingParameters withLimiter(LimiterSettings value) { return new ProcessingParameters(compressor, value, bypass, maximumOutputGainDb); }

    /**
     * Replaces the shared gain applied after mixing.
     *
     * @param value new final gain in dB
     * @return copy with a validated final gain
     */
    public ProcessingParameters withMaximumOutputGainDb(double value) { return new ProcessingParameters(compressor, limiter, bypass, value); }

    /**
     * Rejects NaN, infinities, and values outside a closed interval.
     *
     * @return the unchanged value when valid
     */
    private static double range(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " debe estar entre " + min + " y " + max);
        }
        return value;
    }

    /**
     * Controls for the compressor curve and temporal response.
     *
     * @param enabled whether reduction and makeup are active
     * @param thresholdDb level at which compression begins, in dBFS
     * @param ratio input/output relationship above threshold; 1 means no compression
     * @param attackMs speed of response to rising levels
     * @param releaseMs speed of gain recovery
     * @param kneeDb width of the soft transition around threshold
     * @param makeupGainDb gain applied after compression to compensate level
     */
    public record CompressorSettings(boolean enabled, double thresholdDb, double ratio, double attackMs,
                                     double releaseMs, double kneeDb, double makeupGainDb) {
        /** Validates every control against the limits exposed by the UI. */
        public CompressorSettings {
            thresholdDb = range("Threshold", thresholdDb, -60, 0);
            ratio = range("Ratio", ratio, 1, 20);
            attackMs = range("Attack", attackMs, 0.1, 100);
            releaseMs = range("Release", releaseMs, 20, 2000);
            kneeDb = range("Knee", kneeDb, 0, 24);
            makeupGainDb = range("Makeup", makeupGainDb, -12, 12);
        }
        /**
         * Enables or disables this stage without changing its settings.
         *
         * @param value new enabled state
         * @return copy that changes only that state
         */
        public CompressorSettings withEnabled(boolean value) { return new CompressorSettings(value, thresholdDb, ratio, attackMs, releaseMs, kneeDb, makeupGainDb); }
    }

    /**
     * Controls for the lookahead peak limiter.
     *
     * @param enabled whether protective gain is applied
     * @param ceilingDb maximum permitted output peak, in dBFS
     * @param lookaheadMs lookahead and added latency, in milliseconds
     * @param releaseMs recovery time after a peak disappears
     */
    public record LimiterSettings(boolean enabled, double ceilingDb, double lookaheadMs, double releaseMs) {
        /** Validates every control against the limiter's preallocated capacity. */
        public LimiterSettings {
            ceilingDb = range("Ceiling", ceilingDb, -20, -0.1);
            lookaheadMs = range("Lookahead", lookaheadMs, 0, 20);
            releaseMs = range("Release del limitador", releaseMs, 20, 1000);
        }
        /**
         * Enables or disables this stage without changing its settings.
         *
         * @param value new enabled state
         * @return copy that changes only that state
         */
        public LimiterSettings withEnabled(boolean value) { return new LimiterSettings(value, ceilingDb, lookaheadMs, releaseMs); }
    }
}
