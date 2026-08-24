package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;

/**
 * Stereo-linked feed-forward compressor with a soft-knee transfer curve.
 *
 * <p>The detector takes the greatest L/R magnitude so exactly the same gain is applied
 * to both channels, preserving the stereo image. It first follows the envelope,
 * calculates static reduction from threshold, ratio, and knee, then smooths the
 * resulting gain. Makeup gain is added after reduction.</p>
 */
public final class Compressor {
    /** Stream sample rate used by the temporal followers. */
    private final double sampleRate;
    /** Smoothed detector of maximum stereo amplitude. */
    private final EnvelopeFollower envelope;
    /** Smoother for calculated attenuation. */
    private final GainSmoother gainSmoother;
    /** Settings effective for the next processed sample. */
    private ProcessingParameters.CompressorSettings settings;
    /** Reduction observed at the end of the latest block, expressed as positive dB. */
    private double reductionDb;

    /**
     * Creates the compressor and its state followers.
     *
     * @param sampleRate sample rate in Hz
     * @param settings initial validated configuration
     */
    public Compressor(double sampleRate, ProcessingParameters.CompressorSettings settings) {
        this.sampleRate = sampleRate;
        this.settings = settings;
        this.envelope = new EnvelopeFollower(sampleRate, settings.attackMs(), settings.releaseMs());
        this.gainSmoother = new GainSmoother(sampleRate, settings.attackMs(), settings.releaseMs());
    }

    /**
     * Replaces controls and recomputes timing without resetting dynamic state.
     *
     * @param value new validated configuration
     */
    public void setSettings(ProcessingParameters.CompressorSettings value) {
        if (value.equals(settings)) return;
        settings = value;
        envelope.configure(value.attackMs(), value.releaseMs());
        gainSmoother.configure(value.attackMs(), value.releaseMs());
    }

    /**
     * Compresses a stereo region from {@code input} into {@code output}.
     *
     * @param input normalized L/R samples
     * @param output independent destination with sufficient capacity
     * @param frames stereo pairs to process
     */
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

    /**
     * Evaluates the static compression curve without attack or release.
     *
     * <p>Outside the knee, levels below threshold remain unchanged and higher levels
     * retain only {@code over/ratio}. Inside the knee, a quadratic transition avoids
     * an abrupt slope change.</p>
     *
     * @param inputDb detected level in dBFS
     * @param settings threshold, ratio, and knee width
     * @return required reduction as a positive dB amount
     */
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

    /**
     * Returns the most recent attenuation.
     *
     * @return reduction at the end of the latest block, in positive dB
     */
    public double reductionDb() { return reductionDb; }

    /** Clears detector and gain memory before starting a new stream. */
    public void reset() { envelope.reset(); gainSmoother.reset(); reductionDb = 0.0; }
}
