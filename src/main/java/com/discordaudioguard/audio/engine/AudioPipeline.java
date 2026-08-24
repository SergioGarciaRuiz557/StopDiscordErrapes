package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.api.AudioProcessor;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import com.discordaudioguard.audio.format.Pcm16Decoder;
import com.discordaudioguard.audio.format.Pcm16Encoder;

/**
 * Per-block bridge between binary device PCM and floating-point DSP.
 *
 * <p>The instance preallocates both arrays at construction. Every call decodes
 * little-endian PCM16, processes normalized samples, and re-encodes the result without
 * creating objects or arrays on the capture thread.</p>
 */
public final class AudioPipeline {
    /** Format that determines bytes per frame, channels, and maximum capacity. */
    private final AudioFormatConfiguration format;
    /** DSP chain applied between decoding and encoding. */
    private final AudioProcessor processor;
    /** Shared collector passed to the processor. */
    private final AudioMetrics metrics;
    /** Reusable area for decoded samples. */
    private final float[] inputSamples;
    /** Reusable area for processed samples. */
    private final float[] outputSamples;

    /**
     * Prepares a pipeline whose capacity equals one format block.
     *
     * @param format PCM and maximum-block-size description
     * @param processor processor that receives normalized stereo samples
     * @param metrics destination for DSP telemetry
     */
    public AudioPipeline(AudioFormatConfiguration format, AudioProcessor processor, AudioMetrics metrics) {
        this.format = format; this.processor = processor; this.metrics = metrics;
        inputSamples = new float[format.blockFrames() * format.channels()];
        outputSamples = new float[format.blockFrames() * format.channels()];
    }

    /**
     * Transforms complete frames contained in an initial buffer region.
     *
     * @param inputBytes captured PCM16 LE
     * @param outputBytes PCM16 LE destination with sufficient capacity
     * @param byteCount valid bytes; must represent complete frames of the format
     */
    public void process(byte[] inputBytes, byte[] outputBytes, int byteCount) {
        int frames = byteCount / format.bytesPerFrame();
        Pcm16Decoder.decodeLittleEndian(inputBytes, byteCount, inputSamples);
        processor.process(inputSamples, outputSamples, frames, metrics);
        Pcm16Encoder.encodeLittleEndian(outputSamples, frames * format.channels(), outputBytes);
    }
}
