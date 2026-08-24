package com.discordaudioguard.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Numerical tests for acoustic conversions, non-finite extremes, and clamping. */
class DecibelUtilsTest {
    /** Checks known references: unity and half amplitude. */
    @Test void convertsAmplitudeToDbfs() {
        assertThat(DecibelUtils.amplitudeToDb(1.0)).isCloseTo(0.0, within(1e-9));
        assertThat(DecibelUtils.amplitudeToDb(0.5)).isCloseTo(-6.0206, within(0.001));
    }
    /** Checks inverse conversion for 0 dB and approximately -6.02 dB. */
    @Test void convertsDbToLinearGain() {
        assertThat(DecibelUtils.dbToLinear(-6.0206)).isCloseTo(0.5, within(0.0001));
        assertThat(DecibelUtils.dbToLinear(0)).isEqualTo(1.0);
    }
    /** Ensures finite or bounded results for silence, NaN, and extreme values. */
    @Test void zeroAndExtremeValuesAreSafe() {
        assertThat(DecibelUtils.amplitudeToDb(0)).isEqualTo(DecibelUtils.MINIMUM_DB);
        assertThat(DecibelUtils.amplitudeToDb(Double.NaN)).isEqualTo(DecibelUtils.MINIMUM_DB);
        assertThat(MathUtils.clamp(200, -60, 0)).isEqualTo(0);
        assertThat(MathUtils.clamp(-200, -60, 0)).isEqualTo(-60);
    }
}
