package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;
import com.discordaudioguard.util.MathUtils;

/** Lookahead brickwall limiter with stereo-linked detection and smooth release. */
public final class PeakLimiter {
    private final double sampleRate;
    private final LookaheadBuffer lookahead;
    private ProcessingParameters.LimiterSettings settings;
    private double releaseCoefficient;
    private double gain = 1.0;
    private double reductionDb;

    public PeakLimiter(double sampleRate, ProcessingParameters.LimiterSettings settings) {
        this.sampleRate = sampleRate;
        this.lookahead = new LookaheadBuffer((int) Math.ceil(sampleRate * 0.020));
        setSettings(settings);
    }

    public void setSettings(ProcessingParameters.LimiterSettings value) {
        if (value.equals(settings)) return;
        settings = value;
        releaseCoefficient = DecibelUtils.timeCoefficient(value.releaseMs(), sampleRate);
        lookahead.setDelayFrames((int) Math.round(value.lookaheadMs() * sampleRate / 1_000.0));
    }

    public void processInPlace(float[] samples, int frames) {
        double ceiling = DecibelUtils.dbToLinear(settings.ceilingDb());
        for (int frame = 0, sample = 0; frame < frames; frame++, sample += 2) {
            lookahead.push(samples[sample], samples[sample + 1]);
            double windowPeak = lookahead.peakInWindow();
            double target = settings.enabled() && windowPeak > ceiling ? ceiling / windowPeak : 1.0;
            gain = target < gain ? target : releaseCoefficient * gain + (1.0 - releaseCoefficient) * target;
            float applied = settings.enabled() ? (float) gain : 1.0f;
            samples[sample] = MathUtils.clamp(lookahead.delayedLeft() * applied, -1.0f, 1.0f);
            samples[sample + 1] = MathUtils.clamp(lookahead.delayedRight() * applied, -1.0f, 1.0f);
            lookahead.advance();
        }
        reductionDb = settings.enabled() ? Math.max(0.0, -DecibelUtils.amplitudeToDb(gain)) : 0.0;
    }

    public double reductionDb() { return reductionDb; }
    public int latencyFrames() { return lookahead.delayFrames(); }
    public void reset() { lookahead.reset(); gain = 1.0; reductionDb = 0.0; }
}
