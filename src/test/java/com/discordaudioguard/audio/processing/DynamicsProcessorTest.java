package com.discordaudioguard.audio.processing;

import com.discordaudioguard.audio.engine.AudioMetrics;
import com.discordaudioguard.tools.SignalGenerator;
import com.discordaudioguard.util.DecibelUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Integration tests for the compressor-limiter-bypass chain and its metrics. */
class DynamicsProcessorTest {
    /** Exercises silence and continuous phase across several consecutive blocks. */
    @Test void processesSilenceSineAndConsecutiveBlocks() {
        DynamicsProcessor processor = new DynamicsProcessor(48_000, 256, ProcessingParameters.DEFAULT);
        AudioMetrics metrics = new AudioMetrics(); float[] input = new float[512]; float[] output = new float[512];
        processor.process(input, output, 256, metrics); assertThat(output).containsOnly(0.0f);
        for (int block = 0; block < 5; block++) {
            SignalGenerator.sine(input, 48_000, 1_000, 0.1, block * 256L); processor.process(input, output, 256, metrics);
        }
        assertThat(metrics.snapshot().outputRmsDb()).isGreaterThan(-40);
    }
    /** Demonstrates that a sustained full-scale signal never exceeds the ceiling. */
    @Test void limitsArtificialPeakAndSaturatedSignal() {
        DynamicsProcessor processor = new DynamicsProcessor(48_000, 256, ProcessingParameters.DEFAULT);
        AudioMetrics metrics = new AudioMetrics(); float[] input = new float[512]; float[] output = new float[512];
        for (int block = 0; block < 8; block++) {
            java.util.Arrays.fill(input, 1.0f); processor.process(input, output, 256, metrics);
            for (float sample : output) assertThat(Math.abs(sample)).isLessThanOrEqualTo((float) DecibelUtils.dbToLinear(-2) + 1e-5f);
        }
        assertThat(metrics.snapshot().totalReductionDb()).isGreaterThan(0);
    }
    /** Checks that the parameter mailbox and bypass fade produce finite values. */
    @Test void acceptsHotParameterAndBypassChanges() {
        DynamicsProcessor processor = new DynamicsProcessor(48_000, 256, ProcessingParameters.DEFAULT);
        AudioMetrics metrics = new AudioMetrics(); float[] input = new float[512]; float[] output = new float[512];
        SignalGenerator.sineWithPeak(input, 48_000, 100, true); processor.process(input, output, 256, metrics);
        processor.updateParameters(ProcessingParameters.DEFAULT.withBypass(true));
        for (int i = 0; i < 4; i++) processor.process(input, output, 256, metrics);
        for (float value : output) assertThat(value).isFinite();
    }
}
