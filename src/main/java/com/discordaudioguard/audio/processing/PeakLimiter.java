package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;
import com.discordaudioguard.util.MathUtils;

/**
 * Lookahead brickwall limiter with stereo-linked detection and smooth release.
 *
 * <p>The signal is delayed while the peak across the entire future window is inspected.
 * If that peak exceeds the ceiling, gain immediately falls to the value required to
 * contain it; after it disappears, gain returns exponentially to one. The same gain
 * is applied to L/R, preserving spatial balance.</p>
 */
public final class PeakLimiter {
    /** Sample rate used to size lookahead and calculate release. */
    private final double sampleRate;
    /** Delay line and lookahead detection window. */
    private final LookaheadBuffer lookahead;
    /** Effective configuration. */
    private ProcessingParameters.LimiterSettings settings;
    /** Per-sample coefficient for releasing reduction. */
    private double releaseCoefficient;
    /** Smoothed linear factor currently applied. */
    private double gain = 1.0;
    /** Reduction at the end of the latest block, in positive dB. */
    private double reductionDb;

    /**
     * Allocates the maximum lookahead allowed by the model (20 ms).
     *
     * @param sampleRate sample rate in Hz
     * @param settings initial validated configuration
     */
    public PeakLimiter(double sampleRate, ProcessingParameters.LimiterSettings settings) {
        this.sampleRate = sampleRate;
        this.lookahead = new LookaheadBuffer((int) Math.ceil(sampleRate * 0.020));
        setSettings(settings);
    }

    /**
     * Updates ceiling, lookahead, and release without new allocations.
     *
     * @param value new validated configuration
     */
    public void setSettings(ProcessingParameters.LimiterSettings value) {
        if (value.equals(settings)) return;
        settings = value;
        releaseCoefficient = DecibelUtils.timeCoefficient(value.releaseMs(), sampleRate);
        lookahead.setDelayFrames((int) Math.round(value.lookaheadMs() * sampleRate / 1_000.0));
    }

    /**
     * Limits a stereo region in place.
     *
     * @param samples interleaved L/R samples to be replaced by their delayed values
     * @param frames number of valid pairs
     */
    public void processInPlace(float[] samples, int frames) {
        double ceiling = DecibelUtils.dbToLinear(settings.ceilingDb());
        for (int frame = 0, sample = 0; frame < frames; frame++, sample += 2) {
            lookahead.push(samples[sample], samples[sample + 1]);
            double windowPeak = lookahead.peakInWindow();
            // Instant attack contains the peak; exponential recovery avoids modulation.
            double target = settings.enabled() && windowPeak > ceiling ? ceiling / windowPeak : 1.0;
            gain = target < gain ? target : releaseCoefficient * gain + (1.0 - releaseCoefficient) * target;
            float applied = settings.enabled() ? (float) gain : 1.0f;
            samples[sample] = MathUtils.clamp(lookahead.delayedLeft() * applied, -1.0f, 1.0f);
            samples[sample + 1] = MathUtils.clamp(lookahead.delayedRight() * applied, -1.0f, 1.0f);
            lookahead.advance();
        }
        reductionDb = settings.enabled() ? Math.max(0.0, -DecibelUtils.amplitudeToDb(gain)) : 0.0;
    }

    /**
     * Returns the most recent attenuation.
     *
     * @return reduction at the end of the latest block, in positive dB
     */
    public double reductionDb() { return reductionDb; }

    /**
     * Returns the current lookahead delay.
     *
     * @return algorithmic latency introduced by lookahead, in frames
     */
    public int latencyFrames() { return lookahead.delayFrames(); }

    /** Clears the delay line and restores unity gain. */
    public void reset() { lookahead.reset(); gain = 1.0; reductionDb = 0.0; }
}
