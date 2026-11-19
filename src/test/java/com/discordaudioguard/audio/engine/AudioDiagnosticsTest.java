package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.api.AudioInput;
import com.discordaudioguard.audio.api.AudioOutput;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;

class AudioDiagnosticsTest {
    @TempDir Path directory;
    private final AudioFormatConfiguration format = AudioFormatConfiguration.DEFAULT;

    @Test void convertsVisibleQueuesAndPreservesUnavailableValues() {
        assertThat(AudioDiagnostics.queueMillis(3840, 6144, format)).isEqualTo(20);
        assertThat(AudioDiagnostics.queueMillis(0, 6144, format)).isZero();
        assertThat(AudioDiagnostics.queueMillis(-1, 6144, format)).isNaN();
        assertThat(AudioDiagnostics.queueMillis(6145, 6144, format)).isNaN();
    }

    @Test void recordsSummariesAndFlushesTimestampedMarkerOnImmediateStop() throws Exception {
        FakeInput input = new FakeInput();
        AudioDiagnostics session = session(input, new FakeOutput(), directory);
        session.start();
        try {
            await(() -> session.snapshot().recording());
            assertThat(session.snapshot().inputMillis()).isEqualTo(20);
            assertThat(session.snapshot().outputMillis()).isEqualTo(30);
            assertThat(session.snapshot().visiblePlusDspMillis()).isCloseTo(76.333333, within(0.00001));
            session.read.begin(); session.read.end(512);
            // A pending read must remain visible even if no further audio blocks complete.
            session.write.begin();
            await(() -> contains(session.snapshot().file(), "\"SAMPLE\""));
            double markedAfter = session.snapshot().elapsedMillis();
            assertThat(session.markDelay()).isEqualTo(1);
            session.finish("STOP");
            assertThat(session.awaitFinished(3000)).isTrue();
            assertThat(session.snapshot().savedMarkers()).isEqualTo(1);
            assertThat(session.snapshot().recording()).isFalse();
            assertThat(session.snapshot().status()).isEqualTo("Registro guardado");
            assertThat(session.markDelay()).isEqualTo(-1);
            List<Map<String, String>> rows = rows(session.snapshot().file());
            assertThat(rows.stream().map(r -> r.get("event"))).containsSubsequence("START", "SAMPLE", "MARK", "END");
            Map<String, String> sample = rows.stream().filter(r -> r.get("event").equals("SAMPLE")).findFirst().orElseThrow();
            Map<String, String> mark = rows.stream().filter(r -> r.get("event").equals("MARK")).findFirst().orElseThrow();
            assertThat(Double.parseDouble(sample.get("input_queue_mean_ms"))).isEqualTo(20);
            assertThat(Double.parseDouble(sample.get("output_queue_mean_ms"))).isEqualTo(30);
            assertThat(sample.get("read_units_total")).isEqualTo("512");
            assertThat(Double.parseDouble(sample.get("write_inflight_ms"))).isGreaterThan(100);
            assertThat(Double.parseDouble(mark.get("elapsed_ms"))).isGreaterThanOrEqualTo(markedAfter);
            assertThat(rows.get(0).get("note")).contains("comma, quote\" and newline");
            assertThat(input.pollThread).isEqualTo("discord-audio-guard-diagnostics");
            assertThat(input.pollPriority).isEqualTo(Thread.NORM_PRIORITY);
        } finally { session.finish("TEST"); session.awaitFinished(3000); }
    }

    @Test void unsupportedAndFailingDeviceQueriesDoNotBecomeZeroLatency() throws Exception {
        FakeInput input = new FakeInput(); input.fail = true;
        FakeOutput output = new FakeOutput(); output.queued = -1;
        AudioDiagnostics session = session(input, output, directory);
        session.start();
        try {
            await(() -> session.snapshot().recording());
            assertThat(session.snapshot().inputMillis()).isNaN();
            assertThat(session.snapshot().outputMillis()).isNaN();
            assertThat(session.snapshot().visiblePlusDspMillis()).isNaN();
        } finally { session.finish("STOP"); assertThat(session.awaitFinished(3000)).isTrue(); }
        Map<String, String> end = rows(session.snapshot().file()).getLast();
        assertThat(end.get("input_queue_valid_samples")).isEqualTo("0");
        assertThat(end.get("input_queue_mean_ms")).isEmpty();
        assertThat(end.get("visible_plus_dsp_mean_ms")).isEmpty();
        assertThat(Long.parseLong(end.get("sampling_errors_total"))).isPositive();
    }

    @Test void fileFailureIsReportedWithoutEscapingMonitorThread() throws Exception {
        Path regularFile = directory.resolve("not-a-directory"); Files.writeString(regularFile, "existing");
        AudioDiagnostics session = session(new FakeInput(), new FakeOutput(), regularFile);
        session.start();
        assertThat(session.awaitFinished(3000)).isTrue();
        assertThat(session.snapshot().recording()).isFalse();
        assertThat(session.snapshot().status()).contains("No se pudo guardar");
        assertThat(session.markDelay()).isEqualTo(-1);
        assertThat(Files.readString(regularFile)).isEqualTo("existing");
    }

    private AudioDiagnostics session(FakeInput input, FakeOutput output, Path path) throws Exception {
        AdaptivePcmBuffer buffer = new AdaptivePcmBuffer(3456);
        buffer.putPcm16LittleEndian(new byte[4096], 4096);
        AudioMetrics metrics = new AudioMetrics(); metrics.setLookaheadMillis(5);
        return new AudioDiagnostics(path, format, input, output, buffer, metrics,
                () -> AudioEngineState.RUNNING, "comma, quote\" and\nnewline");
    }

    static void await(BooleanSupplier predicate) throws Exception {
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (!predicate.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(predicate.getAsBoolean()).isTrue();
    }

    private static boolean contains(Path file, String text) {
        try { return Files.exists(file) && Files.readString(file).contains(text); }
        catch (Exception exception) { return false; }
    }

    /** The writer quotes every field; parse escaped quotes and enforce equal column counts. */
    static List<Map<String, String>> rows(Path file) throws Exception {
        List<String> lines = Files.readAllLines(file);
        List<String> header = fields(lines.getFirst());
        return lines.stream().skip(1).map(line -> {
            List<String> values = fields(line);
            assertThat(values).hasSize(header.size());
            Map<String, String> row = new java.util.LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i), values.get(i));
            return row;
        }).toList();
    }

    private static List<String> fields(String line) {
        return java.util.Arrays.stream(line.substring(1, line.length() - 1).split("\",\"", -1))
                .map(value -> value.replace("\"\"", "\"")).toList();
    }

    private static final class FakeInput implements AudioInput {
        volatile String pollThread;
        volatile int pollPriority;
        boolean fail;
        public int queuedBytes() {
            pollThread = Thread.currentThread().getName(); pollPriority = Thread.currentThread().getPriority();
            if (fail) throw new IllegalStateException("unavailable");
            return 3840;
        }
        public long framePosition() { return 123456; }
        public int bufferSizeBytes() { return 6144; }
        public int read(byte[] b, int o, int n) { throw new AssertionError("Monitor must not read PCM"); }
        public void start() {} public void stop() {} public void close() {}
    }

    private static final class FakeOutput implements AudioOutput {
        int queued = 5760;
        public int queuedBytes() { return queued; }
        public long framePosition() { return 120000; }
        public int bufferSizeBytes() { return 6144; }
        public int write(byte[] b, int o, int n) { throw new AssertionError("Monitor must not write PCM"); }
        public void start() {} public void stop() {} public void close() {}
    }
}
