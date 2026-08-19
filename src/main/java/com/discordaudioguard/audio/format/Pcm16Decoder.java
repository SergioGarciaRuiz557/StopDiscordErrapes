package com.discordaudioguard.audio.format;

public final class Pcm16Decoder {
    private Pcm16Decoder() {}

    public static void decodeLittleEndian(byte[] source, int byteCount, float[] destination) {
        int samples = Math.min(byteCount / 2, destination.length);
        for (int i = 0, offset = 0; i < samples; i++, offset += 2) {
            short value = (short) ((source[offset] & 0xff) | (source[offset + 1] << 8));
            destination[i] = value / 32768.0f;
        }
    }
}
