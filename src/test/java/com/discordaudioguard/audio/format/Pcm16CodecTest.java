package com.discordaudioguard.audio.format;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class Pcm16CodecTest {
    @Test void decodesZeroMaximumAndMinimumLittleEndian() {
        byte[] pcm = {0, 0, (byte) 0xff, 0x7f, 0, (byte) 0x80};
        float[] values = new float[3];
        Pcm16Decoder.decodeLittleEndian(pcm, pcm.length, values);
        assertThat(values[0]).isZero();
        assertThat(values[1]).isCloseTo(32767 / 32768.0f, within(1e-7f));
        assertThat(values[2]).isEqualTo(-1.0f);
    }

    @Test void roundTripsStereoSamples() {
        float[] original = {-1.0f, 1.0f, -0.5f, 0.5f, 0.0f, 0.25f};
        byte[] pcm = new byte[original.length * 2]; float[] decoded = new float[original.length];
        Pcm16Encoder.encodeLittleEndian(original, original.length, pcm);
        Pcm16Decoder.decodeLittleEndian(pcm, pcm.length, decoded);
        assertThat(decoded).containsExactly(-1.0f, 32767 / 32768.0f, -0.5f, 16384 / 32768.0f, 0.0f, 8192 / 32768.0f);
    }

    @Test void encoderClampsOutOfRangeValues() {
        float[] input = {-2, 2}; byte[] pcm = new byte[4]; float[] output = new float[2];
        Pcm16Encoder.encodeLittleEndian(input, 2, pcm); Pcm16Decoder.decodeLittleEndian(pcm, 4, output);
        assertThat(output[0]).isEqualTo(-1); assertThat(output[1]).isLessThanOrEqualTo(1);
    }
}
