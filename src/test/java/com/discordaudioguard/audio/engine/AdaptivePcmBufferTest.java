package com.discordaudioguard.audio.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies PCM preservation, clock-correction limits, and adaptive-buffer closure.
 * Different per-channel ramps reveal swaps, repetitions, and endian corruption.
 */
class AdaptivePcmBufferTest {
    /** Confirms that target occupancy plays back exactly at unity speed. */
    @Test void preservesStereoPcmAtUnityRate() throws Exception {
        AdaptivePcmBuffer buffer = new AdaptivePcmBuffer(1024);
        byte[] source = stereoRamp(258);
        byte[] output = new byte[256 * 4];
        assertThat(buffer.putPcm16LittleEndian(source, source.length)).isTrue();

        assertThat(buffer.readPcm16LittleEndian(output, 256, 258)).isEqualTo(output.length);

        assertThat(output).containsExactly(slice(source, output.length));
        assertThat(buffer.playbackRate()).isEqualTo(1.0);
    }

    /** Confirms that high occupancy accelerates consumption without exceeding 0.5%. */
    @Test void consumesSlightlyFasterWhenBufferIsAboveTarget() throws Exception {
        AdaptivePcmBuffer buffer = new AdaptivePcmBuffer(2048);
        byte[] source = stereoRamp(1024);
        buffer.putPcm16LittleEndian(source, source.length);

        buffer.readPcm16LittleEndian(new byte[256 * 4], 256, 512);

        assertThat(buffer.playbackRate()).isGreaterThan(1.0).isLessThanOrEqualTo(1.005);
        assertThat(buffer.bufferedFrames()).isLessThan(1024 - 256);
    }

    /** Confirms that low occupancy slows consumption without leaving the safe range. */
    @Test void consumesSlightlySlowerWhenBufferIsBelowTarget() throws Exception {
        AdaptivePcmBuffer buffer = new AdaptivePcmBuffer(1024);
        buffer.putPcm16LittleEndian(stereoRamp(300), 300 * 4);

        buffer.readPcm16LittleEndian(new byte[256 * 4], 256, 512);

        assertThat(buffer.playbackRate()).isLessThan(1.0).isGreaterThanOrEqualTo(0.995);
        assertThat(buffer.bufferedFrames()).isGreaterThan(300 - 256);
    }

    /** Ensures that closing with insufficient data signals end instead of replaying stale memory. */
    @Test void doesNotReplayStaleSamplesWhenClosedWithAnIncompleteBlock() throws Exception {
        AdaptivePcmBuffer buffer = new AdaptivePcmBuffer(1024);
        buffer.putPcm16LittleEndian(stereoRamp(100), 100 * 4);
        buffer.close();

        assertThat(buffer.readPcm16LittleEndian(new byte[256 * 4], 256, 512)).isEqualTo(-1);
    }

    /** Builds predictable PCM frames with different slopes in L and R. */
    private static byte[] stereoRamp(int frames) {
        byte[] result = new byte[frames * 4];
        for (int frame = 0, offset = 0; frame < frames; frame++, offset += 4) {
            short left = (short) (frame * 31 - 10_000);
            short right = (short) (10_000 - frame * 17);
            encode(result, offset, left);
            encode(result, offset + 2, right);
        }
        return result;
    }

    /** Encodes one helper sample in little-endian order. */
    private static void encode(byte[] destination, int offset, short value) {
        destination[offset] = (byte) value;
        destination[offset + 1] = (byte) (value >>> 8);
    }

    /** Copies an initial region so only the expected output block is compared. */
    private static byte[] slice(byte[] source, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, 0, result, 0, length);
        return result;
    }
}
