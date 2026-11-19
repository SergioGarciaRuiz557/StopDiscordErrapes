package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.api.*;
import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import com.discordaudioguard.audio.processing.ProcessingParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;

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
    @TempDir Path diagnosticsDirectory;

    @Test void restartingOutputKeepsCaptureAndProcessingActive() throws Exception {
        ReopeningBackend backend = new ReopeningBackend();
        CopyProcessor processor = new CopyProcessor();
        Path session;
        try (AudioEngine engine = new AudioEngine(backend, AudioFormatConfiguration.DEFAULT,
                processor, new AudioMetrics(), 4, diagnosticsDirectory)) {
            assertThat(engine.restartOutput()).isFalse();
            engine.start(device("input"), device("output"), true);
            AudioDiagnosticsTest.await(() -> engine.state() == AudioEngineState.RUNNING && engine.diagnosticSnapshot().recording());
            session = engine.diagnosticSnapshot().file();
            assertThat(engine.markDelay()).isEqualTo(1);
            assertThat(engine.restartOutput()).isTrue();
            assertThat(backend.opening.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(engine.restartOutput()).isFalse();
            backend.allowOpen.countDown();
            AudioDiagnosticsTest.await(() -> backend.replacement.started && backend.replacement.writes.getCount() == 0);
            int processed = processor.blocks;
            AudioDiagnosticsTest.await(() -> processor.blocks > processed);
            assertThat(backend.output.closed).isTrue();
            assertThat(backend.replacement.closed).isFalse();
            assertThat(backend.input.closed).isFalse();
            assertThat(backend.inputOpens).isOne();
            assertThat(processor.resets).isOne();
            assertThat(engine.diagnosticSnapshot().file()).isEqualTo(session);
            assertThat(engine.markDelay()).isEqualTo(2);
        }
        assertThat(backend.replacement.closed).isTrue();
        assertThat(backend.input.closed).isTrue();
        assertThat(AudioDiagnosticsTest.rows(session)).extracting(row -> row.get("event"))
                .containsSubsequence("START", "MARK", "OUTPUT_RESTART_BEGIN", "OUTPUT_RESTARTED", "END");
    }

    @Test void stopDuringReopenReleasesLateOutput() throws Exception {
        ReopeningBackend backend = new ReopeningBackend();
        Path session;
        try (AudioEngine engine = new AudioEngine(backend, AudioFormatConfiguration.DEFAULT,
                new CopyProcessor(), new AudioMetrics(), 4, diagnosticsDirectory)) {
            engine.start(device("input"), device("output"), true);
            AudioDiagnosticsTest.await(() -> engine.state() == AudioEngineState.RUNNING && engine.diagnosticSnapshot().recording());
            session = engine.diagnosticSnapshot().file();
            assertThat(engine.restartOutput()).isTrue();
            assertThat(backend.opening.await(2, TimeUnit.SECONDS)).isTrue();
            engine.stop();
            backend.allowOpen.countDown();
            awaitStopped(engine);
            assertThat(backend.replacement.closed).isTrue();
            assertThat(backend.replacement.started).isFalse();
            assertThat(backend.input.closed).isTrue();
            assertThat(engine.restartOutput()).isFalse();
        }
        assertThat(AudioDiagnosticsTest.rows(session)).extracting(row -> row.get("event"))
                .containsSubsequence("OUTPUT_RESTART_BEGIN", "OUTPUT_RESTART_CANCELLED", "END")
                .doesNotContain("OUTPUT_RESTARTED");
    }

    @Test void failedOutputReopenReleasesCaptureAndReportsError() throws Exception {
        ReopeningBackend backend = new ReopeningBackend();
        backend.failOpen = true;
        backend.allowOpen.countDown();
        Path session;
        try (AudioEngine engine = new AudioEngine(backend, AudioFormatConfiguration.DEFAULT,
                new CopyProcessor(), new AudioMetrics(), 4, diagnosticsDirectory)) {
            engine.start(device("input"), device("output"), true);
            AudioDiagnosticsTest.await(() -> engine.state() == AudioEngineState.RUNNING && engine.diagnosticSnapshot().recording());
            session = engine.diagnosticSnapshot().file();
            assertThat(engine.restartOutput()).isTrue();
            AudioDiagnosticsTest.await(() -> engine.state() == AudioEngineState.ERROR && backend.input.closed);
            assertThat(backend.output.closed).isTrue();
            assertThat(engine.restartOutput()).isFalse();
        }
        assertThat(AudioDiagnosticsTest.rows(session)).extracting(row -> row.get("event"))
                .containsSubsequence("OUTPUT_RESTART_BEGIN", "OUTPUT_RESTART_FAILED", "END")
                .doesNotContain("OUTPUT_RESTARTED");
    }

    @Test void diagnosticSessionsSurviveStopRestartAndCloseWithoutLosingMarks() throws Exception {
        FakeBackend backend = new FakeBackend();
        AudioEngine engine = new AudioEngine(backend, AudioFormatConfiguration.DEFAULT, new CopyProcessor(),
                new AudioMetrics(), 4, diagnosticsDirectory);
        try {
            engine.start(device("input"), device("output"), true);
            AudioDiagnosticsTest.await(() -> engine.state() == AudioEngineState.RUNNING && engine.diagnosticSnapshot().recording());
            Path first = engine.diagnosticSnapshot().file();
            assertThat(engine.markDelay()).isEqualTo(1);
            engine.stop(); awaitStopped(engine);
            AudioDiagnosticsTest.await(() -> !engine.diagnosticSnapshot().recording());
            assertThat(Files.readString(first)).contains("\"MARK\"", "\"END\"", "\"STOP\"");
            engine.start(device("input"), device("output"), true);
            AudioDiagnosticsTest.await(() -> engine.state() == AudioEngineState.RUNNING && engine.diagnosticSnapshot().recording());
            Path second = engine.diagnosticSnapshot().file();
            assertThat(second).isNotEqualTo(first);
            assertThat(engine.diagnosticSnapshot().savedMarkers()).isZero();
            engine.close();
            assertThat(Files.readString(second)).contains("\"START\"", "\"END\"");
        } finally { engine.close(); }
    }

    @Test void diagnosticsFailureDoesNotStopAudio() throws Exception {
        Path regularFile = diagnosticsDirectory.resolve("file"); Files.writeString(regularFile, "keep");
        AudioEngine engine = new AudioEngine(new FakeBackend(), AudioFormatConfiguration.DEFAULT,
                new CopyProcessor(), new AudioMetrics(), 4, regularFile);
        try {
            engine.start(device("input"), device("output"), true);
            AudioDiagnosticsTest.await(() -> engine.diagnosticSnapshot().status().contains("No se pudo guardar"));
            awaitRunning(engine);
            assertThat(engine.state()).isEqualTo(AudioEngineState.RUNNING);
        } finally { engine.close(); }
    }
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
    private static class FakeBackend implements AudioBackend {
        /** Shared endpoints whose state can be observed by the test. */
        final FakeInput input = new FakeInput(); final FakeOutput output = new FakeOutput();
        /** Signal released by the first write. */
        final CountDownLatch writes = output.writes;
        @Override public List<AudioDeviceDescriptor> scanDevices(AudioFormatConfiguration format) { return List.of(); }
        @Override public AudioInput openInput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks) { input.closed = false; return input; }
        @Override public AudioOutput openOutput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks) { output.closed = false; output.started = false; return output; }
    }
    /** A driver can return a newly opened line even after cancellation was requested. */
    private static final class ReopeningBackend extends FakeBackend {
        final FakeOutput replacement = new FakeOutput();
        final CountDownLatch opening = new CountDownLatch(1);
        final CountDownLatch allowOpen = new CountDownLatch(1);
        volatile int inputOpens;
        int outputOpens;
        boolean failOpen;

        @Override public AudioInput openInput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks) {
            inputOpens++;
            return super.openInput(device, format, bufferBlocks);
        }

        @Override public AudioOutput openOutput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks) {
            if (outputOpens++ == 0) return super.openOutput(device, format, bufferBlocks);
            opening.countDown();
            try {
                if (!allowOpen.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for reopen");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            if (failOpen) throw new IllegalStateException("Salida ocupada");
            return replacement;
        }
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
        volatile int resets;
        @Override public void process(float[] input, float[] output, int frames, AudioMetrics metrics) {
            System.arraycopy(input, 0, output, 0, frames * 2); blocks++;
        }
        @Override public void updateParameters(ProcessingParameters parameters) {}
        @Override public int latencyFrames() { return 0; }
        @Override public void reset() { blocks = 0; resets++; }
    }
}
