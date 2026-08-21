package com.discordaudioguard.audio.processing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CompressorTest {
    private static ProcessingParameters.CompressorSettings settings(double ratio, double knee) {
        return new ProcessingParameters.CompressorSettings(true, -18, ratio, 0.1, 20, knee, 0);
    }

    @Test void transferCurveDoesNothingBelowOrAtThreshold() {
        assertThat(Compressor.calculateReductionDb(-30, settings(6, 0))).isZero();
        assertThat(Compressor.calculateReductionDb(-18, settings(6, 0))).isZero();
    }
    @Test void transferCurveCompressesAboveThreshold() {
        assertThat(Compressor.calculateReductionDb(-6, settings(6, 0))).isCloseTo(10.0, within(1e-9));
        assertThat(Compressor.calculateReductionDb(-6, settings(20, 0))).isCloseTo(11.4, within(1e-9));
        assertThat(Compressor.calculateReductionDb(-6, settings(1, 0))).isZero();
    }
    @Test void softKneeBeginsProgressively() {
        double belowKnee = Compressor.calculateReductionDb(-22, settings(6, 6));
        double insideKnee = Compressor.calculateReductionDb(-18, settings(6, 6));
        double aboveKnee = Compressor.calculateReductionDb(-12, settings(6, 6));
        assertThat(belowKnee).isZero(); assertThat(insideKnee).isBetween(0.0, aboveKnee);
    }
    @Test void attackAndReleaseAreSmoothedAndStereoLinked() {
        Compressor compressor = new Compressor(48_000, settings(6, 0));
        float[] loud = new float[4_800 * 2]; java.util.Arrays.fill(loud, 1.0f); float[] out = new float[loud.length];
        compressor.process(loud, out, 4_800);
        assertThat(compressor.reductionDb()).isGreaterThan(10);
        assertThat(out[out.length - 2]).isCloseTo(out[out.length - 1], within(1e-6f));
        float[] quiet = new float[4_800 * 2]; compressor.process(quiet, out, 4_800);
        assertThat(compressor.reductionDb()).isLessThan(15).isGreaterThan(0);
    }
    @Test void settingsCanChangeAtRuntime() {
        Compressor compressor = new Compressor(48_000, settings(1, 0));
        float[] in = new float[9_600]; java.util.Arrays.fill(in, 1); float[] out = new float[in.length];
        compressor.process(in, out, 4_800); assertThat(compressor.reductionDb()).isZero();
        compressor.setSettings(settings(20, 0)); compressor.process(in, out, 4_800);
        assertThat(compressor.reductionDb()).isGreaterThan(10);
    }
}
