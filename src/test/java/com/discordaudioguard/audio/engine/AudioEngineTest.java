package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.api.*;
import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import com.discordaudioguard.audio.processing.ProcessingParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class AudioEngineTest {
    @Test void streamsThroughSimulatedDevicesAndClosesThem() throws Exception {
        FakeBackend backend = new FakeBackend(); AudioMetrics metrics = new AudioMetrics(); CopyProcessor processor = new CopyProcessor();
        AudioEngine engine = new AudioEngine(backend, AudioFormatConfiguration.DEFAULT, processor, metrics, 4);
        engine.start(device("input"), device("output"));
        assertThat(backend.writes.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(engine.state()).isEqualTo(AudioEngineState.RUNNING);
        assertThat(processor.blocks).isGreaterThan(0);
        engine.stop();
        awaitStopped(engine);
        assertThat(backend.input.closed).isTrue(); assertThat(backend.output.closed).isTrue();
    }

    @Test void rejectsDuplicateStartsAndPotentialLoop() throws Exception {
        FakeBackend backend = new FakeBackend();
        AudioEngine engine = new AudioEngine(backend, AudioFormatConfiguration.DEFAULT, new CopyProcessor(), new AudioMetrics(), 4);
        assertThatThrownBy(() -> engine.start(device("same"), device("same"))).hasMessageContaining("bucle");
        engine.start(device("input"), device("output"));
        assertThat(backend.writes.await(2, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> engine.start(device("other-input"), device("other-output")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("iniciado");
        engine.stop(); awaitStopped(engine);
    }

    private static AudioDeviceDescriptor device(String id) {
        return new AudioDeviceDescriptor(id, id, "simulado", "tests", "1", true, true, true);
    }
    private static void awaitStopped(AudioEngine engine) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (engine.state() != AudioEngineState.STOPPED && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(engine.state()).isEqualTo(AudioEngineState.STOPPED);
    }

    private static final class FakeBackend implements AudioBackend {
        final FakeInput input = new FakeInput(); final FakeOutput output = new FakeOutput();
        final CountDownLatch writes = output.writes;
        @Override public List<AudioDeviceDescriptor> scanDevices(AudioFormatConfiguration format) { return List.of(); }
        @Override public AudioInput openInput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks) { return input; }
        @Override public AudioOutput openOutput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks) { return output; }
    }
    private static final class FakeInput implements AudioInput {
        volatile boolean closed;
        @Override public void start() {}
        @Override public int read(byte[] buffer, int offset, int length) {
            if (closed) return -1;
            for (int i = offset; i < offset + length; i++) buffer[i] = 0;
            return length;
        }
        @Override public void stop() {}
        @Override public int bufferSizeBytes() { return 4096; }
        @Override public void close() { closed = true; }
    }
    private static final class FakeOutput implements AudioOutput {
        final CountDownLatch writes = new CountDownLatch(1); volatile boolean closed;
        @Override public void start() {}
        @Override public int write(byte[] buffer, int offset, int length) { writes.countDown(); return closed ? 0 : length; }
        @Override public void stop() {}
        @Override public int bufferSizeBytes() { return 4096; }
        @Override public void close() { closed = true; }
    }
    private static final class CopyProcessor implements AudioProcessor {
        volatile int blocks;
        @Override public void process(float[] input, float[] output, int frames, AudioMetrics metrics) {
            System.arraycopy(input, 0, output, 0, frames * 2); blocks++;
        }
        @Override public void updateParameters(ProcessingParameters parameters) {}
        @Override public int latencyFrames() { return 0; }
        @Override public void reset() { blocks = 0; }
    }
}
