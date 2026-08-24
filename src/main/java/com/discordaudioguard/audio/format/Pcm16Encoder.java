package com.discordaudioguard.audio.format;

import com.discordaudioguard.util.MathUtils;

/** Stateless encoder from normalized samples to little-endian PCM16. */
public final class Pcm16Encoder {
    /** Prevents instantiation of this purely static class. */
    private Pcm16Encoder() {}

    /**
     * Clamps each sample to the representable interval and encodes it as signed PCM.
     * Different factors are used on each half-axis to map -1.0 exactly to -32768 and
     * +1.0 to +32767 without overflowing the positive endpoint.
     *
     * @param source normalized samples preserving channel order
     * @param samples number of samples to convert
     * @param destination destination providing two bytes per sample
     */
    public static void encodeLittleEndian(float[] source, int samples, byte[] destination) {
        int count = Math.min(samples, destination.length / 2);
        for (int i = 0, offset = 0; i < count; i++, offset += 2) {
            float clamped = MathUtils.clamp(source[i], -1.0f, 1.0f);
            int value = clamped < 0.0f ? Math.round(clamped * 32768.0f) : Math.round(clamped * 32767.0f);
            destination[offset] = (byte) value;
            destination[offset + 1] = (byte) (value >>> 8);
        }
    }
}
