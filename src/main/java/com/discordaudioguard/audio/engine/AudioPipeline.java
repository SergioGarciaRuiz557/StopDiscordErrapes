package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.api.AudioProcessor;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import com.discordaudioguard.audio.format.Pcm16Decoder;
import com.discordaudioguard.audio.format.Pcm16Encoder;

public final class AudioPipeline {
    private final AudioFormatConfiguration format;
    private final AudioProcessor processor;
    private final AudioMetrics metrics;
    private final float[] inputSamples;
    private final float[] outputSamples;

    public AudioPipeline(AudioFormatConfiguration format, AudioProcessor processor, AudioMetrics metrics) {
        this.format = format; this.processor = processor; this.metrics = metrics;
        inputSamples = new float[format.blockFrames() * format.channels()];
        outputSamples = new float[format.blockFrames() * format.channels()];
    }

    public void process(byte[] inputBytes, byte[] outputBytes, int byteCount) {
        int frames = byteCount / format.bytesPerFrame();
        Pcm16Decoder.decodeLittleEndian(inputBytes, byteCount, inputSamples);
        processor.process(inputSamples, outputSamples, frames, metrics);
        Pcm16Encoder.encodeLittleEndian(outputSamples, frames * format.channels(), outputBytes);
    }
}
