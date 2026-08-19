package com.discordaudioguard.audio.processing;

import com.discordaudioguard.audio.api.AudioProcessor;
import com.discordaudioguard.audio.engine.AudioMetrics;
import com.discordaudioguard.util.DecibelUtils;
import com.discordaudioguard.util.MathUtils;

import java.util.concurrent.atomic.AtomicReference;

/** Allocation-free processing chain for a fixed maximum block size. */
public final class DynamicsProcessor implements AudioProcessor {
    private static final double BYPASS_FADE_MS = 5.0;
    private final Compressor compressor;
    private final PeakLimiter limiter;
    private final LevelMeter inputMeter = new LevelMeter();
    private final LevelMeter outputMeter = new LevelMeter();
    private final AtomicReference<ProcessingParameters> pendingParameters;
    private final float[] wetBuffer;
    private final double bypassStep;
    private ProcessingParameters activeParameters;
    private double wetMix;

    public DynamicsProcessor(double sampleRate, int maximumBlockFrames, ProcessingParameters parameters) {
        activeParameters = parameters.validated();
        pendingParameters = new AtomicReference<>(activeParameters);
        compressor = new Compressor(sampleRate, parameters.compressor());
        limiter = new PeakLimiter(sampleRate, parameters.limiter());
        wetBuffer = new float[maximumBlockFrames * 2];
        bypassStep = 1.0 / Math.max(1.0, BYPASS_FADE_MS * 0.001 * sampleRate);
        wetMix = parameters.bypass() ? 0.0 : 1.0;
    }

    @Override
    public void process(float[] input, float[] output, int frames, AudioMetrics metrics) {
        applyPendingParameters();
        inputMeter.measure(input, frames);
        compressor.process(input, wetBuffer, frames);
        limiter.processInPlace(wetBuffer, frames);
        float finalGain = (float) DecibelUtils.dbToLinear(activeParameters.maximumOutputGainDb());
        double targetWet = activeParameters.bypass() ? 0.0 : 1.0;
        for (int i = 0; i < frames * 2; i += 2) {
            if (wetMix < targetWet) wetMix = Math.min(targetWet, wetMix + bypassStep);
            else if (wetMix > targetWet) wetMix = Math.max(targetWet, wetMix - bypassStep);
            float dryMix = (float) (1.0 - wetMix);
            float currentWet = (float) wetMix;
            output[i] = MathUtils.clamp((wetBuffer[i] * currentWet + input[i] * dryMix) * finalGain, -1.0f, 1.0f);
            output[i + 1] = MathUtils.clamp((wetBuffer[i + 1] * currentWet + input[i + 1] * dryMix) * finalGain, -1.0f, 1.0f);
        }
        outputMeter.measure(output, frames);
        metrics.publishLevels(inputMeter, outputMeter, compressor.reductionDb(), limiter.reductionDb());
    }

    private void applyPendingParameters() {
        ProcessingParameters next = pendingParameters.get();
        if (next == activeParameters) return;
        activeParameters = next;
        compressor.setSettings(next.compressor());
        limiter.setSettings(next.limiter());
    }

    @Override public void updateParameters(ProcessingParameters parameters) { pendingParameters.set(parameters.validated()); }
    @Override public int latencyFrames() { return limiter.latencyFrames(); }
    @Override public void reset() { compressor.reset(); limiter.reset(); }
}
