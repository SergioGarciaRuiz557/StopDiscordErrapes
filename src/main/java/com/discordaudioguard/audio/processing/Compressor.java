package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;

/** Stereo-linked feed-forward compressor with a soft-knee transfer curve. */
public final class Compressor {
    private final double sampleRate;
    private final EnvelopeFollower envelope;
    private final GainSmoother gainSmoother;
    private ProcessingParameters.CompressorSettings settings;
    private double reductionDb;

    public Compressor(double sampleRate, ProcessingParameters.CompressorSettings settings) {
        this.sampleRate = sampleRate;
        this.settings = settings;
        this.envelope = new EnvelopeFollower(sampleRate, settings.attackMs(), settings.releaseMs());
        this.gainSmoother = new GainSmoother(sampleRate, settings.attackMs(), settings.releaseMs());
    }

    public void setSettings(ProcessingParameters.CompressorSettings value) {
        if (value.equals(settings)) return;
        settings = value;
        envelope.configure(value.attackMs(), value.releaseMs());
        gainSmoother.configure(value.attackMs(), value.releaseMs());
    }

    public void process(float[] input, float[] output, int frames) {
        double lastReduction = 0.0;
        for (int frame = 0, sample = 0; frame < frames; frame++, sample += 2) {
            double detector = Math.max(Math.abs(input[sample]), Math.abs(input[sample + 1]));
            double levelDb = DecibelUtils.amplitudeToDb(envelope.process(detector));
            double targetReduction = settings.enabled() ? calculateReductionDb(levelDb, settings) : 0.0;
            double smoothedGainDb = gainSmoother.process(-targetReduction);
            double gainDb = smoothedGainDb + (settings.enabled() ? settings.makeupGainDb() : 0.0);
            float gain = (float) DecibelUtils.dbToLinear(gainDb);
            output[sample] = input[sample] * gain;
            output[sample + 1] = input[sample + 1] * gain;
            lastReduction = Math.max(0.0, -smoothedGainDb);
        }
        reductionDb = lastReduction;
    }

    public static double calculateReductionDb(double inputDb, ProcessingParameters.CompressorSettings settings) {
        double over = inputDb - settings.thresholdDb();
        double knee = settings.kneeDb();
        double compressedOver;
        if (knee > 0.0 && over > -knee / 2.0 && over < knee / 2.0) {
            double x = over + knee / 2.0;
            compressedOver = (1.0 / settings.ratio() - 1.0) * x * x / (2.0 * knee);
            return Math.max(0.0, -compressedOver);
        }
        if (over <= 0.0) return 0.0;
        return over - over / settings.ratio();
    }

    public double reductionDb() { return reductionDb; }
    public void reset() { envelope.reset(); gainSmoother.reset(); reductionDb = 0.0; }
}
