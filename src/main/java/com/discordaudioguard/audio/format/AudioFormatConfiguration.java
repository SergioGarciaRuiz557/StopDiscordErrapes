package com.discordaudioguard.audio.format;

import javax.sound.sampled.AudioFormat;

public record AudioFormatConfiguration(float sampleRate, int sampleSizeBits, int channels,
                                       boolean signed, boolean bigEndian, int blockFrames) {
    public static final AudioFormatConfiguration DEFAULT = new AudioFormatConfiguration(48_000, 16, 2, true, false, 256);

    public AudioFormatConfiguration {
        if (sampleRate <= 0 || sampleSizeBits != 16 || channels != 2 || blockFrames < 32 || blockFrames > 4096) {
            throw new IllegalArgumentException("Formato no válido: se requiere PCM estéreo de 16 bits y un bloque de 32 a 4096 frames");
        }
    }

    public AudioFormat toJavaSoundFormat() {
        return new AudioFormat(sampleRate, sampleSizeBits, channels, signed, bigEndian);
    }

    public int bytesPerFrame() { return channels * (sampleSizeBits / 8); }
    public int blockBytes() { return blockFrames * bytesPerFrame(); }
    public double blockDurationMillis() { return blockFrames * 1_000.0 / sampleRate; }
}
