package com.discordaudioguard.audio.api;

import com.discordaudioguard.audio.engine.AudioMetrics;
import com.discordaudioguard.audio.processing.ProcessingParameters;

/**
 * Contract for a stereo DSP chain operating on normalized samples.
 *
 * <p>Arrays contain interleaved left and right channels with nominal amplitudes in
 * [-1, 1]. Implementations run on the capture thread and must avoid locks, I/O, and
 * per-block allocations.</p>
 */
public interface AudioProcessor {
    /**
     * Processes input frames and writes the result to a separate buffer.
     *
     * @param input interleaved L/R input samples
     * @param output destination with capacity for at least {@code frames * 2} samples
     * @param frames number of valid stereo pairs
     * @param metrics collector in which levels and reduction are published
     */
    void process(float[] input, float[] output, int frames, AudioMetrics metrics);

    /**
     * Requests use of a complete parameter set on a subsequent block.
     * The call may originate outside the audio thread.
     *
     * @param parameters new validated snapshot of DSP controls
     */
    void updateParameters(ProcessingParameters parameters);

    /**
     * Reports the delay required to align audio and diagnostics.
     *
     * @return algorithmic delay introduced by the processor, in frames
     */
    int latencyFrames();

    /** Clears temporary memory, envelopes, and gains before a new stream. */
    void reset();
}
