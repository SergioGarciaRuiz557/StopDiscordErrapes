package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.MathUtils;

public record ProcessingParameters(CompressorSettings compressor, LimiterSettings limiter,
                                   boolean bypass, double maximumOutputGainDb) {
    public static final ProcessingParameters DEFAULT = new ProcessingParameters(
            new CompressorSettings(true, -18.0, 6.0, 3.0, 250.0, 6.0, 0.0),
            new LimiterSettings(true, -2.0, 5.0, 150.0), false, 0.0);

    public ProcessingParameters {
        if (compressor == null || limiter == null) throw new IllegalArgumentException("Los parámetros DSP no pueden ser nulos");
        maximumOutputGainDb = range("Ganancia máxima", maximumOutputGainDb, -30, 0);
    }

    public ProcessingParameters validated() { return this; }
    public ProcessingParameters withBypass(boolean value) { return new ProcessingParameters(compressor, limiter, value, maximumOutputGainDb); }
    public ProcessingParameters withCompressor(CompressorSettings value) { return new ProcessingParameters(value, limiter, bypass, maximumOutputGainDb); }
    public ProcessingParameters withLimiter(LimiterSettings value) { return new ProcessingParameters(compressor, value, bypass, maximumOutputGainDb); }
    public ProcessingParameters withMaximumOutputGainDb(double value) { return new ProcessingParameters(compressor, limiter, bypass, value); }

    private static double range(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " debe estar entre " + min + " y " + max);
        }
        return value;
    }

    public record CompressorSettings(boolean enabled, double thresholdDb, double ratio, double attackMs,
                                     double releaseMs, double kneeDb, double makeupGainDb) {
        public CompressorSettings {
            thresholdDb = range("Threshold", thresholdDb, -60, 0);
            ratio = range("Ratio", ratio, 1, 20);
            attackMs = range("Attack", attackMs, 0.1, 100);
            releaseMs = range("Release", releaseMs, 20, 2000);
            kneeDb = range("Knee", kneeDb, 0, 24);
            makeupGainDb = range("Makeup", makeupGainDb, -12, 12);
        }
        public CompressorSettings withEnabled(boolean value) { return new CompressorSettings(value, thresholdDb, ratio, attackMs, releaseMs, kneeDb, makeupGainDb); }
    }

    public record LimiterSettings(boolean enabled, double ceilingDb, double lookaheadMs, double releaseMs) {
        public LimiterSettings {
            ceilingDb = range("Ceiling", ceilingDb, -20, -0.1);
            lookaheadMs = range("Lookahead", lookaheadMs, 0, 20);
            releaseMs = range("Release del limitador", releaseMs, 20, 1000);
        }
        public LimiterSettings withEnabled(boolean value) { return new LimiterSettings(value, ceilingDb, lookaheadMs, releaseMs); }
    }
}
