package com.discordaudioguard.audio.processing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProcessingParametersTest {
    @Test void rejectsValuesOutsideSafeRanges() {
        assertThatThrownBy(() -> new ProcessingParameters.CompressorSettings(true, -61, 6, 3, 250, 6, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Threshold");
        assertThatThrownBy(() -> new ProcessingParameters.LimiterSettings(true, 0, 5, 150))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ceiling");
        assertThatThrownBy(() -> ProcessingParameters.DEFAULT.withMaximumOutputGainDb(1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
