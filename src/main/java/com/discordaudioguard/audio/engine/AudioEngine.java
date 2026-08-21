package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.api.*;
import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import com.discordaudioguard.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public final class AudioEngine implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioEngine.class);
    private static final int MAX_ZERO_READS = 200;
    private final AudioBackend backend;
    private final AudioFormatConfiguration format;
    private final AudioProcessor processor;
    private final AudioMetrics metrics;
    private final int bufferBlocks;
    private final AtomicReference<AudioEngineState> state = new AtomicReference<>(AudioEngineState.STOPPED);
    private final CopyOnWriteArrayList<BiConsumer<AudioEngineState, String>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean stopRequested;
    private volatile Thread worker;
    private volatile AudioInput activeInput;
    private volatile AudioOutput activeOutput;

    public AudioEngine(AudioBackend backend, AudioFormatConfiguration format, AudioProcessor processor,
                       AudioMetrics metrics, int bufferBlocks) {
        this.backend = Objects.requireNonNull(backend); this.format = format; this.processor = processor;
        this.metrics = metrics; this.bufferBlocks = bufferBlocks;
    }

    public synchronized void start(AudioDeviceDescriptor inputDevice, AudioDeviceDescriptor outputDevice) {
        if (!state.compareAndSet(AudioEngineState.STOPPED, AudioEngineState.STARTING) &&
                !state.compareAndSet(AudioEngineState.ERROR, AudioEngineState.STARTING)) {
            throw new IllegalStateException("El motor ya está iniciado o cambiando de estado");
        }
        if (inputDevice.id().equals(outputDevice.id())) {
            state.set(AudioEngineState.STOPPED);
            throw new IllegalArgumentException("La entrada y la salida seleccionadas son el mismo dispositivo. Posible bucle de audio detectado.");
        }
        stopRequested = false;
        publish(AudioEngineState.STARTING, "Abriendo dispositivos de audio…");
        worker = ThreadUtils.daemonThread("discord-audio-guard-realtime", () -> runAudio(inputDevice, outputDevice));
        worker.start();
    }

    private void runAudio(AudioDeviceDescriptor inputDevice, AudioDeviceDescriptor outputDevice) {
        byte[] inputBuffer = new byte[format.blockBytes()];
        byte[] outputBuffer = new byte[format.blockBytes()];
        AudioPipeline pipeline = new AudioPipeline(format, processor, metrics);
        try (AudioInput input = backend.openInput(inputDevice, format, bufferBlocks);
             AudioOutput output = backend.openOutput(outputDevice, format, bufferBlocks)) {
            activeInput = input; activeOutput = output;
            processor.reset();
            input.start(); output.start();
            double bufferLatency = (input.bufferSizeBytes() + output.bufferSizeBytes()) /
                    (double) format.bytesPerFrame() * 1_000.0 / format.sampleRate();
            metrics.setEstimatedLatencyMillis(bufferLatency + processor.latencyFrames() * 1_000.0 / format.sampleRate());
            state.set(AudioEngineState.RUNNING);
            publish(AudioEngineState.RUNNING, "Procesamiento de audio activo");
            int zeroReads = 0;
            while (!stopRequested && !Thread.currentThread().isInterrupted()) {
                int bytesRead = readBlock(input, inputBuffer);
                if (stopRequested) break;
                if (bytesRead == 0) {
                    if (++zeroReads >= MAX_ZERO_READS) throw new IllegalStateException("La entrada devolvió cero bytes repetidamente; el dispositivo puede haberse desconectado");
                    Thread.onSpinWait();
                    continue;
                }
                zeroReads = 0;
                if (bytesRead < 0) throw new IllegalStateException("El dispositivo de entrada fue desconectado");
                long started = System.nanoTime();
                pipeline.process(inputBuffer, outputBuffer, bytesRead);
                long elapsed = System.nanoTime() - started;
                int written = writeBlock(output, outputBuffer, bytesRead);
                if (written < bytesRead) metrics.incrementWriteErrors();
                metrics.blockCompleted(elapsed, format.blockDurationMillis());
            }
        } catch (Throwable exception) {
            if (!stopRequested) {
                LOGGER.error("El motor de audio se detuvo inesperadamente", exception);
                state.set(AudioEngineState.ERROR);
                publish(AudioEngineState.ERROR, "El motor se ha detenido inesperadamente: " + userMessage(exception));
            }
        } finally {
            activeInput = null; activeOutput = null;
            if (state.get() != AudioEngineState.ERROR) {
                state.set(AudioEngineState.STOPPED);
                publish(AudioEngineState.STOPPED, "Procesamiento detenido");
            }
        }
    }

    private int readBlock(AudioInput input, byte[] buffer) {
        int total = 0;
        while (total < buffer.length && !stopRequested) {
            int count = input.read(buffer, total, buffer.length - total);
            if (count <= 0) return total == 0 ? count : total;
            total += count;
        }
        return total;
    }

    private int writeBlock(AudioOutput output, byte[] buffer, int length) {
        int total = 0;
        while (total < length && !stopRequested) {
            int count = output.write(buffer, total, length - total);
            if (count <= 0) break;
            total += count;
        }
        return total;
    }

    public synchronized void stop() {
        AudioEngineState current = state.get();
        if (current == AudioEngineState.STOPPED || current == AudioEngineState.STOPPING) return;
        stopRequested = true;
        state.set(AudioEngineState.STOPPING);
        publish(AudioEngineState.STOPPING, "Cerrando dispositivos…");
        safeStop(activeInput); safeStop(activeOutput);
        Thread thread = worker;
        if (thread != null) thread.interrupt();
    }

    private static void safeStop(AudioInput input) { if (input != null) try { input.stop(); input.close(); } catch (RuntimeException ignored) {} }
    private static void safeStop(AudioOutput output) { if (output != null) try { output.stop(); output.close(); } catch (RuntimeException ignored) {} }

    public AudioEngineState state() { return state.get(); }
    public AudioMetrics metrics() { return metrics; }
    public void addStateListener(BiConsumer<AudioEngineState, String> listener) { listeners.add(listener); }
    private void publish(AudioEngineState value, String message) { for (var listener : listeners) listener.accept(value, message); }
    private static String userMessage(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    @Override public void close() { stop(); }
}
