package com.discordaudioguard.audio.format;

import javax.sound.sampled.AudioFormat;

/**
 * Fixed PCM format and processing granularity used by the engine.
 *
 * <p>The current implementation accepts only 16-bit linear stereo PCM. {@code signed}
 * and {@code bigEndian} remain explicit to construct {@link AudioFormat}, although the
 * codec pipeline uses little endian.</p>
 *
 * @param sampleRate frames per second, normally 48 kHz
 * @param sampleSizeBits bits per sample; currently required to be 16
 * @param channels interleaved channels; currently required to be 2
 * @param signed whether PCM samples are signed
 * @param bigEndian whether the most significant byte appears first
 * @param blockFrames frames processed per pipeline invocation
 */
public record AudioFormatConfiguration(float sampleRate, int sampleSizeBits, int channels,
                                       boolean signed, boolean bigEndian, int blockFrames) {
    /** Default operating format: 48 kHz, PCM16 LE, stereo, 256 frames. */
    public static final AudioFormatConfiguration DEFAULT = new AudioFormatConfiguration(48_000, 16, 2, true, false, 256);

    /** Validates constraints assumed by the rest of the pipeline. */
    public AudioFormatConfiguration {
        if (sampleRate <= 0 || sampleSizeBits != 16 || channels != 2 || blockFrames < 32 || blockFrames > 4096) {
            throw new IllegalArgumentException("Formato no válido: se requiere PCM estéreo de 16 bits y un bloque de 32 a 4096 frames");
        }
    }

    /**
     * Converts the persistable model to Java Sound's native type.
     *
     * @return equivalent representation required by the API
     */
    public AudioFormat toJavaSoundFormat() {
        return new AudioFormat(sampleRate, sampleSizeBits, channels, signed, bigEndian);
    }

    /**
     * Computes the binary width of one multichannel instant.
     *
     * @return bytes occupied jointly by every channel in one frame
     */
    public int bytesPerFrame() { return channels * (sampleSizeBits / 8); }

    /**
     * Computes the size of the pipeline's binary arrays.
     *
     * @return byte capacity required for one complete block
     */
    public int blockBytes() { return blockFrames * bytesPerFrame(); }

    /**
     * Converts processing granularity to real time.
     *
     * @return duration of one block in milliseconds
     */
    public double blockDurationMillis() { return blockFrames * 1_000.0 / sampleRate; }
}
