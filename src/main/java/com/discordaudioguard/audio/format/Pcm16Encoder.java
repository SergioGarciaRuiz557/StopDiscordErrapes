package com.discordaudioguard.audio.format;

import com.discordaudioguard.util.MathUtils;

public final class Pcm16Encoder {
    private Pcm16Encoder() {}

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
