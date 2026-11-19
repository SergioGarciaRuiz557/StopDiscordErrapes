package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.api.AudioInput;
import com.discordaudioguard.audio.api.AudioOutput;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Optional, per-stream diagnostic recorder. Device polling, formatting and file
 * access happen on a normal-priority monitor. Audio threads enqueue restart events.
 * Samples are approximate: hidden driver queues and in-flight blocks are not included.
 * Files contain numeric telemetry and device names, never PCM or recorded speech.
 */
public final class AudioDiagnostics {
    static final long SAMPLE_MILLIS = 100;
    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final String[] VALUE_NAMES = {
            "input_queue", "adaptive_queue", "output_queue", "visible_pending",
            "lookahead", "visible_plus_dsp", "rate_ppm"
    };
    private static final String[] PROBE_NAMES = {"read", "dsp", "put", "take", "write"};

    final AudioTimingProbe read = new AudioTimingProbe();
    final AudioTimingProbe dsp = new AudioTimingProbe();
    final AudioTimingProbe put = new AudioTimingProbe();
    final AudioTimingProbe take = new AudioTimingProbe();
    final AudioTimingProbe write = new AudioTimingProbe();
    private final AudioTimingProbe[] probes = {read, dsp, put, take, write};
    private final AudioTimingProbe.Snapshot[] previous = new AudioTimingProbe.Snapshot[5];
    private final AudioTimingProbe.Snapshot[] latest = new AudioTimingProbe.Snapshot[5];
    private final AudioFormatConfiguration format;
    private final AudioInput input;
    private final Supplier<AudioOutput> currentOutput;
    private final AdaptivePcmBuffer buffer;
    private final AudioMetrics metrics;
    private final Supplier<AudioEngineState> engineState;
    private final Path file;
    private final String context;
    private final long startedNanos = System.nanoTime();
    private final Instant startedAt = Instant.now();
    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
    private final CountDownLatch wake = new CountDownLatch(1);
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Window window = new Window();
    private volatile String endReason;
    private volatile Snapshot snapshot;
    private int markerCount;
    // Even revisions identify active lines; an odd revision means an output restart is in progress.
    private volatile int outputRevision;
    private int sampledOutputRevision = -1;
    private long samplingErrors, writtenBytes, inputPosition = -1, outputPosition = -1;
    private long previousSampleNanos;
    private double maxSampleGapMillis;
    private final long initialBlocks, initialWriteErrors;

    AudioDiagnostics(Path directory, AudioFormatConfiguration format, AudioInput input,
                     Supplier<AudioOutput> currentOutput, AdaptivePcmBuffer buffer, AudioMetrics metrics,
                     Supplier<AudioEngineState> engineState, String context) {
        this.format = format;
        this.input = input;
        this.currentOutput = currentOutput;
        this.buffer = buffer;
        this.metrics = metrics;
        this.engineState = engineState;
        this.context = context;
        file = directory.resolve("audio-" + FILE_TIME.format(startedAt) + "-" + UUID.randomUUID() + ".csv");
        AudioMetrics.Snapshot baseline = metrics.snapshot();
        initialBlocks = baseline.blocksProcessed();
        initialWriteErrors = baseline.writeErrors();
        for (int i = 0; i < probes.length; i++) previous[i] = latest[i] = AudioTimingProbe.Snapshot.EMPTY;
        snapshot = new Snapshot("Preparando registro…", file, false, 0, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
    }

    public static Path directory() {
        return Path.of(System.getProperty("user.home"), ".discord-audio-guard", "diagnostics");
    }

    void start() {
        Thread monitor = new Thread(this::run, "discord-audio-guard-diagnostics");
        monitor.setDaemon(true);
        monitor.setPriority(Thread.NORM_PRIORITY);
        monitor.start();
    }

    /** Called by the UI. The timestamp is captured here, not when the disk writer runs. */
    public synchronized int markDelay() {
        if (!snapshot.recording() || endReason != null) return -1;
        int next = markerCount + 1;
        if (markerCount - snapshot.savedMarkers() >= 32) return -1;
        events.add(new Event("MARK", Instant.now(), elapsedMillis(), next, "User noticed delay"));
        markerCount = next;
        return next;
    }

    synchronized void outputRestartEvent(String event) {
        if (endReason != null || finished.getCount() == 0) return;
        if (event.equals("OUTPUT_RESTART_BEGIN") || event.equals("OUTPUT_RESTARTED")) outputRevision++;
        events.add(new Event(event, Instant.now(), elapsedMillis(), 0, ""));
    }

    /** Nonblocking shutdown request, safe to call on an audio thread. */
    synchronized void finish(String reason) {
        if (endReason == null) endReason = reason;
        wake.countDown();
    }

    boolean awaitFinished(long timeoutMillis) throws InterruptedException {
        return finished.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public Snapshot snapshot() { return snapshot; }

    public static Snapshot disabledSnapshot() {
        return new Snapshot("Diagnóstico desactivado", null, false, 0, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
    }

    private void run() {
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                writeLine(writer, header());
                writeEvent(writer, "START", startedAt, 0, "", context +
                        "; sample_ms=100; summary_ms=1000; UTC timestamps; blank=unavailable; " +
                        "queue maxima are sampled; timing maxima are cumulative; " +
                        "visible_plus_dsp excludes hidden queues and in-flight blocks; " +
                        "output_frame_position is local to output_position_generation; schema=2");
                writer.flush();
                long nextSummary = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                while (endReason == null) {
                    sample();
                    drainEvents(writer);
                    long now = System.nanoTime();
                    if (now >= nextSummary) {
                        writeSummary(writer, "SAMPLE", "");
                        writer.flush();
                        nextSummary = now + TimeUnit.SECONDS.toNanos(1);
                        if (writtenBytes >= MAX_FILE_BYTES) {
                            finish("LIMIT");
                            drainEvents(writer);
                            writeSummary(writer, "LIMIT", "Recording stopped at 100 MiB; audio continues");
                            publishStatus("Límite de 100 MiB alcanzado; registro detenido", false);
                            return;
                        }
                    }
                    wake.await(SAMPLE_MILLIS, TimeUnit.MILLISECONDS);
                }
                // No device calls after stop: retain the last sample and flush pending marks.
                drainEvents(writer);
                writeSummary(writer, "END", endReason);
                writer.flush();
                publishStatus("Registro guardado", false);
            }
        } catch (IOException | RuntimeException exception) {
            publishStatus("No se pudo guardar el diagnóstico: " + exception.getMessage(), false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            publishStatus("Registro interrumpido", false);
        } finally {
            finished.countDown();
        }
    }

    private void sample() {
        long now = System.nanoTime();
        if (previousSampleNanos != 0) {
            maxSampleGapMillis = Math.max(maxSampleGapMillis, (now - previousSampleNanos) / 1_000_000.0);
        }
        previousSampleNanos = now;
        double inputMillis = Double.NaN, outputMillis = Double.NaN;
        try { inputMillis = queueMillis(input.queuedBytes(), input.bufferSizeBytes(), format); }
        catch (RuntimeException ignored) { samplingErrors++; }
        try { inputPosition = input.framePosition(); }
        catch (RuntimeException ignored) { inputPosition = -1; samplingErrors++; }
        int revision = outputRevision;
        AudioOutput output = currentOutput.get();
        outputPosition = -1;
        sampledOutputRevision = -1;
        if ((revision & 1) == 0 && output != null) {
            int outputErrors = 0;
            try { outputMillis = queueMillis(output.queuedBytes(), output.bufferSizeBytes(), format); }
            catch (RuntimeException ignored) { outputErrors++; }
            try { outputPosition = output.framePosition(); }
            catch (RuntimeException ignored) { outputErrors++; }
            if (revision == outputRevision && output == currentOutput.get()) {
                sampledOutputRevision = revision;
                samplingErrors += outputErrors;
            } else {
                outputMillis = Double.NaN;
                outputPosition = -1;
            }
        }
        double adaptiveMillis = buffer.bufferedFrames() * 1_000.0 / format.sampleRate();
        double visible = inputMillis + adaptiveMillis + outputMillis;
        double lookahead = metrics.lookaheadMillis();
        double ppm = (buffer.playbackRate() - 1.0) * 1_000_000.0;
        window.add(inputMillis, adaptiveMillis, outputMillis, visible, lookahead, visible + lookahead, ppm);
        for (int i = 0; i < probes.length; i++) {
            AudioTimingProbe.Snapshot value = probes[i].snapshot();
            if (value != null) latest[i] = value;
        }
        Snapshot last = snapshot;
        snapshot = new Snapshot("Registrando", file, true, elapsedMillis(), last.savedMarkers(),
                inputMillis, adaptiveMillis, outputMillis, visible + lookahead, now);
    }

    static double queueMillis(int bytes, int capacityBytes, AudioFormatConfiguration format) {
        if (bytes < 0 || capacityBytes < 0 || bytes > capacityBytes) return Double.NaN;
        return bytes * 1_000.0 / (format.bytesPerFrame() * (double) format.sampleRate());
    }

    private void drainEvents(BufferedWriter writer) throws IOException {
        Event event;
        while ((event = events.poll()) != null) {
            writeEvent(writer, event.type(), event.at(), event.elapsedMillis(),
                    event.marker() == 0 ? "" : Integer.toString(event.marker()), event.note());
            writer.flush();
            if (event.marker() == 0) continue;
            Snapshot last = snapshot;
            snapshot = new Snapshot(last.status(), file, last.recording(), last.elapsedMillis(), event.marker(),
                    last.inputMillis(), last.adaptiveMillis(), last.outputMillis(), last.visiblePlusDspMillis(),
                    last.sampleNanos());
        }
    }

    private void writeSummary(BufferedWriter writer, String event, String note) throws IOException {
        List<String> values = prefix(event, Instant.now(), elapsedMillis(), "");
        values.add(engineState.get().name());
        values.add(Integer.toString(window.samples));
        values.add(number(maxSampleGapMillis));
        for (int i = 0; i < VALUE_NAMES.length; i++) {
            values.add(Integer.toString(window.counts[i]));
            values.add(number(window.mean(i)));
            values.add(number(window.maximum(i)));
        }
        values.add(number(window.samples == 0 ? Double.NaN : window.saturated * 100.0 / window.samples));
        boolean currentPosition = sampledOutputRevision >= 0 && sampledOutputRevision == outputRevision;
        values.add(position(inputPosition)); values.add(currentPosition ? position(outputPosition) : "");
        AudioMetrics.Snapshot m = metrics.snapshot();
        values.add(Long.toString(m.blocksProcessed() - initialBlocks));
        values.add(Long.toString(m.writeErrors() - initialWriteErrors));
        values.add(Long.toString(samplingErrors));
        for (int i = 0; i < probes.length; i++) {
            AudioTimingProbe.Snapshot current = probes[i].snapshot();
            if (current == null) current = latest[i];
            long count = current.count() - previous[i].count();
            values.add(Long.toString(current.count()));
            values.add(Long.toString(current.units()));
            values.add(number(count == 0 ? Double.NaN :
                    (current.totalNanos() - previous[i].totalNanos()) / (double) count / 1_000_000.0));
            values.add(number(current.maximumNanos() / 1_000_000.0));
            values.add(number(current.activeNanos() / 1_000_000.0));
            previous[i] = current;
        }
        values.add(currentPosition ? Integer.toString(1 + sampledOutputRevision / 2) : "");
        values.add(note);
        writeLine(writer, values);
        window.clear();
        maxSampleGapMillis = 0;
    }

    private void writeEvent(BufferedWriter writer, String event, Instant at, double elapsed,
                            String marker, String note) throws IOException {
        List<String> values = prefix(event, at, elapsed, marker);
        values.add(engineState.get().name());
        while (values.size() < header().size() - 1) values.add("");
        values.add(note);
        writeLine(writer, values);
    }

    private List<String> prefix(String event, Instant at, double elapsed, String marker) {
        return new ArrayList<>(List.of(event, at.toString(), number(elapsed), marker));
    }

    private static List<String> header() {
        List<String> fields = new ArrayList<>(List.of("event", "utc", "elapsed_ms", "marker", "engine_state",
                "samples", "sample_gap_max_ms"));
        for (String name : VALUE_NAMES) {
            String unit = name.equals("rate_ppm") ? "" : "_ms";
            fields.add(name + "_valid_samples");
            fields.add(name + "_mean" + unit);
            fields.add(name + "_max" + unit);
        }
        fields.addAll(List.of("rate_limit_percent", "input_frame_position", "output_frame_position",
                "blocks_total", "write_errors_total", "sampling_errors_total"));
        for (String name : PROBE_NAMES) {
            fields.addAll(List.of(name + "_operations_total", name + "_units_total", name + "_mean_ms",
                    name + "_max_session_ms", name + "_inflight_ms"));
        }
        fields.add("output_position_generation");
        fields.add("note");
        return fields;
    }

    private void writeLine(BufferedWriter writer, List<String> values) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i != 0) line.append(',');
            line.append('"').append(values.get(i).replace("\r", " ").replace("\n", " ").replace("\"", "\"\""))
                    .append('"');
        }
        line.append('\n');
        String text = line.toString();
        writer.write(text);
        writtenBytes += text.getBytes(StandardCharsets.UTF_8).length;
    }

    private void publishStatus(String status, boolean recording) {
        Snapshot last = snapshot;
        snapshot = new Snapshot(status, file, recording, elapsedMillis(), last.savedMarkers(),
                last.inputMillis(), last.adaptiveMillis(), last.outputMillis(), last.visiblePlusDspMillis(),
                last.sampleNanos());
    }

    private double elapsedMillis() { return (System.nanoTime() - startedNanos) / 1_000_000.0; }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.4f", value) : ""; }
    private static String position(long value) { return value < 0 ? "" : Long.toString(value); }
    private record Event(String type, Instant at, double elapsedMillis, int marker, String note) {}

    /** Values are read together by the UI. sampleNanos allows it to flag a stale monitor. */
    public record Snapshot(String status, Path file, boolean recording, double elapsedMillis, int savedMarkers,
                           double inputMillis, double adaptiveMillis, double outputMillis,
                           double visiblePlusDspMillis, long sampleNanos) {}

    private static final class Window {
        private final double[] sums = new double[7], maxima = new double[7];
        private final int[] counts = new int[7];
        private int samples, saturated;
        void add(double... values) {
            samples++;
            if (Math.abs(values[6]) >= 4999) saturated++;
            for (int i = 0; i < values.length; i++) if (Double.isFinite(values[i])) {
                sums[i] += values[i];
                maxima[i] = counts[i] == 0 ? values[i] : Math.max(maxima[i], values[i]);
                counts[i]++;
            }
        }
        double mean(int i) { return counts[i] == 0 ? Double.NaN : sums[i] / counts[i]; }
        double maximum(int i) { return counts[i] == 0 ? Double.NaN : maxima[i]; }
        void clear() {
            java.util.Arrays.fill(sums, 0); java.util.Arrays.fill(counts, 0);
            samples = saturated = 0;
        }
    }
}
