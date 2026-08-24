package com.discordaudioguard.audio.processing;

import com.discordaudioguard.audio.api.AudioProcessor;
import com.discordaudioguard.audio.engine.AudioMetrics;
import com.discordaudioguard.util.DecibelUtils;
import com.discordaudioguard.util.MathUtils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Dynamics DSP chain with no per-block allocations and click-free bypass.
 *
 * <p>The order is input metering, compressor, limiter, dry/wet bypass fade, final
 * maximum gain, safety clamp, and output metering. The wet buffer is allocated for the
 * maximum size at construction.</p>
 *
 * <p>The UI publishes an immutable object through {@link #pendingParameters}. The audio
 * thread adopts it at the beginning of a block and updates components without locks.
 * Bypass interpolates over 5 ms so toggling it does not introduce an audible discontinuity.</p>
 */
public final class DynamicsProcessor implements AudioProcessor {
    /** Duration of the fade between processed and direct audio. */
    private static final double BYPASS_FADE_MS = 5.0;
    /** First stage: gradual dynamics control. */
    private final Compressor compressor;
    /** Second stage: absolute peak protection. */
    private final PeakLimiter limiter;
    /** Meter before DSP. */
    private final LevelMeter inputMeter = new LevelMeter();
    /** Meter after DSP and final gain. */
    private final LevelMeter outputMeter = new LevelMeter();
    /** Lock-free mailbox for parameters sent from another thread. */
    private final AtomicReference<ProcessingParameters> pendingParameters;
    /** Preallocated intermediate wet result. */
    private final float[] wetBuffer;
    /** Per-frame mix increment that completes the fade in 5 ms. */
    private final double bypassStep;
    /** Parameters currently applied by the DSP thread. */
    private ProcessingParameters activeParameters;
    /** Current processed proportion between zero (dry) and one (wet). */
    private double wetMix;

    /**
     * Creates every stage and allocates the maximum workspace.
     *
     * @param sampleRate sample rate in Hz
     * @param maximumBlockFrames maximum capacity of each call
     * @param parameters complete initial parameters
     */
    public DynamicsProcessor(double sampleRate, int maximumBlockFrames, ProcessingParameters parameters) {
        activeParameters = parameters.validated();
        pendingParameters = new AtomicReference<>(activeParameters);
        compressor = new Compressor(sampleRate, parameters.compressor());
        limiter = new PeakLimiter(sampleRate, parameters.limiter());
        wetBuffer = new float[maximumBlockFrames * 2];
        bypassStep = 1.0 / Math.max(1.0, BYPASS_FADE_MS * 0.001 * sampleRate);
        wetMix = parameters.bypass() ? 0.0 : 1.0;
    }

    /** {@inheritDoc} */
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

    /** Adopts the latest UI-published configuration once per block. */
    private void applyPendingParameters() {
        ProcessingParameters next = pendingParameters.get();
        if (next == activeParameters) return;
        activeParameters = next;
        compressor.setSettings(next.compressor());
        limiter.setSettings(next.limiter());
    }

    /** {@inheritDoc} */
    @Override public void updateParameters(ProcessingParameters parameters) { pendingParameters.set(parameters.validated()); }

    /** The limiter lookahead is the only source of algorithmic latency. */
    @Override public int latencyFrames() { return limiter.latencyFrames(); }

    /** Clears dynamic state while preserving selected parameters. */
    @Override public void reset() { compressor.reset(); limiter.reset(); }
}
