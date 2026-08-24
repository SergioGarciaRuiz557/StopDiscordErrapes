package com.discordaudioguard.audio.format;

/** Stateless decoder from little-endian PCM16 to normalized {@code float} samples. */
public final class Pcm16Decoder {
    /** Prevents instantiation of this purely static class. */
    private Pcm16Decoder() {}

    /**
     * Decodes consecutive samples while preserving interleaved channel order.
     * Signed PCM asymmetry is normalized by dividing by 32768, so -32768 represents
     * -1.0 and +32767 remains just below +1.0.
     *
     * @param source little-endian PCM bytes
     * @param byteCount valid count in {@code source}; a final odd byte is ignored
     * @param destination normalized-sample destination
     */
    public static void decodeLittleEndian(byte[] source, int byteCount, float[] destination) {
        int samples = Math.min(byteCount / 2, destination.length);
        for (int i = 0, offset = 0; i < samples; i++, offset += 2) {
            short value = (short) ((source[offset] & 0xff) | (source[offset + 1] << 8));
            destination[i] = value / 32768.0f;
        }
    }
}
