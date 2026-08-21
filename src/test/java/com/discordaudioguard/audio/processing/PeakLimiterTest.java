package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PeakLimiterTest {
    private static final double CEILING = DecibelUtils.dbToLinear(-2);
    private static PeakLimiter limiter(double lookahead) {
        return new PeakLimiter(48_000, new ProcessingParameters.LimiterSettings(true, -2, lookahead, 50));
    }
    @Test void leavesSignalBelowCeilingUnchangedAfterDelay() {
        PeakLimiter limiter = limiter(1); float[] data = new float[200 * 2]; java.util.Arrays.fill(data, 0.25f);
        limiter.processInPlace(data, 200);
        assertThat(data[150]).isCloseTo(0.25f, within(1e-6f));
    }
    @Test void neverExceedsCeilingAndLinksStereoChannels() {
        PeakLimiter limiter = limiter(5); float[] data = new float[600 * 2];
        data[400] = 1.0f; data[401] = 0.25f;
        limiter.processInPlace(data, 600);
        float maximum = 0; for (float value : data) maximum = Math.max(maximum, Math.abs(value));
        assertThat(maximum).isLessThanOrEqualTo((float) CEILING + 1e-6f);
        int delayedPeak = 400 + 2 * limiter.latencyFrames();
        assertThat(Math.abs(data[delayedPeak + 1]) / Math.abs(data[delayedPeak])).isCloseTo(0.25f, within(1e-4f));
        assertThat(limiter.reductionDb()).isGreaterThan(0);
    }
    @Test void catchesSingleSamplePeakWithZeroLookahead() {
        PeakLimiter limiter = limiter(0); float[] data = {0, 0, 1, 1, 0, 0};
        limiter.processInPlace(data, 3);
        assertThat(data[2]).isLessThanOrEqualTo((float) CEILING + 1e-6f);
    }
    @Test void releaseRecoversGradually() {
        PeakLimiter limiter = limiter(0); float[] peak = {1, 1}; limiter.processInPlace(peak, 1);
        float[] normal = new float[2_000]; java.util.Arrays.fill(normal, 0.2f); limiter.processInPlace(normal, 1_000);
        assertThat(Math.abs(normal[0])).isLessThan(Math.abs(normal[normal.length - 2]));
        assertThat(Math.abs(normal[normal.length - 2])).isLessThan(0.2f);
    }
}
