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

/**
 * Tests the engine lifecycle using fully simulated devices.
 *
 * <p>Test doubles remove dependence on machine hardware and expose preloading,
 * processing, closure, and rejection of illegal transitions.</p>
 */
class AudioEngineTest {
    /** Exercises the full stream and verifies pre-start preloading and final release. */
    @Test void streamsThroughSimulatedDevicesAndClosesThem() throws Exception {
        FakeBackend backend = new FakeBackend(); AudioMetrics metrics = new AudioMetrics(); CopyProcessor processor = new CopyProcessor();
        AudioEngine engine = new AudioEngine(backend, AudioFormatConfiguration.DEFAULT, processor, metrics, 4);
        engine.start(device("input"), device("output"));
        assertThat(backend.writes.await(2, TimeUnit.SECONDS)).isTrue();
        awaitRunning(engine);
        assertThat(engine.state()).isEqualTo(AudioEngineState.RUNNING);
        assertThat(processor.blocks).isGreaterThan(0);
        assertThat(backend.output.writesBeforeStart).isGreaterThanOrEqualTo(1);
        engine.stop();
        awaitStopped(engine);
        assertThat(backend.input.closed).isTrue(); assertThat(backend.output.closed).isTrue();
    }

    /** Checks both start protections: physical loop and concurrent second start. */
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

    /** Builds a compatible duplex descriptor that differs only by identity. */
    private static AudioDeviceDescriptor device(String id) {
        return new AudioDeviceDescriptor(id, id, "simulado", "tests", "1", true, true, true);
    }
    /** Waits a bounded time for asynchronous shutdown to reach STOPPED. */
    private static void awaitStopped(AudioEngine engine) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (engine.state() != AudioEngineState.STOPPED && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(engine.state()).isEqualTo(AudioEngineState.STOPPED);
    }
    /** Waits a bounded time for the STARTING phase to end. */
    private static void awaitRunning(AudioEngine engine) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (engine.state() == AudioEngineState.STARTING && System.nanoTime() < deadline) Thread.sleep(5);
    }

    /** Deterministic backend that always returns the same simulated endpoint pair. */
    private static final class FakeBackend implements AudioBackend {
        /** Shared endpoints whose state can be observed by the test. */
        final FakeInput input = new FakeInput(); final FakeOutput output = new FakeOutput();
        /** Signal released by the first write. */
        final CountDownLatch writes = output.writes;
        @Override public List<AudioDeviceDescriptor> scanDevices(AudioFormatConfiguration format) { return List.of(); }
        @Override public AudioInput openInput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks) { return input; }
        @Override public AudioOutput openOutput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks) { return output; }
    }
    /** Infinite silent input that ends when closed by the engine. */
    private static final class FakeInput implements AudioInput {
        /** Flag visible to both the test and capture threads. */
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
    /** Output that accepts blocks and records whether they arrived before its clock started. */
    private static final class FakeOutput implements AudioOutput {
        /** Synchronizes the test with the first write and publishes lifecycle flags. */
        final CountDownLatch writes = new CountDownLatch(1); volatile boolean closed; volatile boolean started;
        /** Number of blocks used by preloading before {@link #start()}. */
        volatile int writesBeforeStart;
        @Override public void start() { started = true; }
        @Override public int write(byte[] buffer, int offset, int length) {
            if (!started) writesBeforeStart++;
            writes.countDown();
            return closed ? 0 : length;
        }
        @Override public void stop() {}
        @Override public int bufferSizeBytes() { return 4096; }
        @Override public void close() { closed = true; }
    }
    /** Identity DSP that counts invocations without introducing latency. */
    private static final class CopyProcessor implements AudioProcessor {
        /** Blocks processed since the latest reset. */
        volatile int blocks;
        @Override public void process(float[] input, float[] output, int frames, AudioMetrics metrics) {
            System.arraycopy(input, 0, output, 0, frames * 2); blocks++;
        }
        @Override public void updateParameters(ProcessingParameters parameters) {}
        @Override public int latencyFrames() { return 0; }
        @Override public void reset() { blocks = 0; }
    }
}
