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
    private static final int MINIMUM_ADAPTIVE_BUFFER_BLOCKS = 8;
    private static final int MAXIMUM_DEVICE_PREFILL_BLOCKS = 11;
    private final AudioBackend backend;
    private final AudioFormatConfiguration format;
    private final AudioProcessor processor;
    private final AudioMetrics metrics;
    private final int bufferBlocks;
    private final AtomicReference<AudioEngineState> state = new AtomicReference<>(AudioEngineState.STOPPED);
    private final CopyOnWriteArrayList<BiConsumer<AudioEngineState, String>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean stopRequested;
    private volatile Thread worker;
    private volatile Thread captureWorker;
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
        try (AudioInput input = backend.openInput(inputDevice, format, bufferBlocks);
             AudioOutput output = backend.openOutput(outputDevice, format, bufferBlocks)) {
            activeInput = input; activeOutput = output;
            if (stopRequested) return;
            processor.reset();
            int outputBufferBlocks = Math.max(1, output.bufferSizeBytes() / format.blockBytes());
            int prefillBlocks = Math.max(1, Math.min(MAXIMUM_DEVICE_PREFILL_BLOCKS, outputBufferBlocks - 1));
            int targetBufferBlocks = Math.max(MINIMUM_ADAPTIVE_BUFFER_BLOCKS, bufferBlocks * 2);
            int capacityBlocks = targetBufferBlocks + prefillBlocks + 8;
            int targetBufferFrames = targetBufferBlocks * format.blockFrames();
            AdaptivePcmBuffer adaptiveBuffer = new AdaptivePcmBuffer(capacityBlocks * format.blockFrames());
            AtomicReference<Throwable> captureFailure = new AtomicReference<>();
            AudioPipeline pipeline = new AudioPipeline(format, processor, metrics);
            byte[] playbackBuffer = new byte[format.blockBytes()];
            Thread capture = ThreadUtils.daemonThread("discord-audio-guard-capture",
                    () -> captureAudio(input, pipeline, adaptiveBuffer, captureFailure));
            captureWorker = capture;
            input.start();
            capture.start();
            try {
                int startupFrames = (targetBufferBlocks + prefillBlocks) * format.blockFrames() + 2;
                if (!adaptiveBuffer.awaitFrames(startupFrames)) {
                    throwCaptureFailure(captureFailure);
                }
                // SourceDataLine accepts data before start(). Keep both a device-side reserve
                // and an adaptive reserve that can absorb scheduling jitter and clock drift.
                for (int i = 0; i < prefillBlocks && !stopRequested; i++) {
                    int length = readPlaybackBlock(adaptiveBuffer, playbackBuffer, targetBufferFrames, captureFailure);
                    if (length < 0) break;
                    if (writeBlock(output, playbackBuffer, length) < length) metrics.incrementWriteErrors();
                }
                if (stopRequested) return;
                output.start();
                double bufferLatency = (targetBufferBlocks + prefillBlocks) * format.blockDurationMillis();
                metrics.setEstimatedLatencyMillis(bufferLatency + processor.latencyFrames() * 1_000.0 / format.sampleRate());
                LOGGER.info("Audio iniciado: entrada={}, salida={}, bloque={} frames, búfer adaptativo={} bloques, precarga={} bloques, buffers de dispositivo={}/{} bytes",
                        inputDevice.name(), outputDevice.name(), format.blockFrames(), targetBufferBlocks, prefillBlocks,
                        input.bufferSizeBytes(), output.bufferSizeBytes());
                state.set(AudioEngineState.RUNNING);
                publish(AudioEngineState.RUNNING, "Procesamiento de audio activo");

                while (!stopRequested && !Thread.currentThread().isInterrupted()) {
                    int length = readPlaybackBlock(adaptiveBuffer, playbackBuffer, targetBufferFrames, captureFailure);
                    if (length < 0) break;
                    if (writeBlock(output, playbackBuffer, length) < length) metrics.incrementWriteErrors();
                }
            } finally {
                adaptiveBuffer.close();
                safeStop(input);
                capture.interrupt();
                joinCapture(capture);
            }
        } catch (Throwable exception) {
            if (!stopRequested) {
                LOGGER.error("El motor de audio se detuvo inesperadamente", exception);
                state.set(AudioEngineState.ERROR);
                publish(AudioEngineState.ERROR, "El motor se ha detenido inesperadamente: " + userMessage(exception));
            }
        } finally {
            activeInput = null; activeOutput = null; captureWorker = null;
            if (state.get() != AudioEngineState.ERROR) {
                state.set(AudioEngineState.STOPPED);
                publish(AudioEngineState.STOPPED, "Procesamiento detenido");
            }
        }
    }

    private void captureAudio(AudioInput input, AudioPipeline pipeline, AdaptivePcmBuffer adaptiveBuffer,
                              AtomicReference<Throwable> captureFailure) {
        byte[] inputBuffer = new byte[format.blockBytes()];
        byte[] outputBuffer = new byte[format.blockBytes()];
        int zeroReads = 0;
        try {
            while (!stopRequested && !Thread.currentThread().isInterrupted()) {
                int bytesRead = readBlock(input, inputBuffer);
                if (stopRequested) break;
                if (bytesRead == 0) {
                    if (++zeroReads >= MAX_ZERO_READS) {
                        throw new IllegalStateException("La entrada devolvió cero bytes repetidamente; el dispositivo puede haberse desconectado");
                    }
                    Thread.onSpinWait();
                    continue;
                }
                zeroReads = 0;
                if (bytesRead < 0) throw new IllegalStateException("El dispositivo de entrada fue desconectado");
                long started = System.nanoTime();
                pipeline.process(inputBuffer, outputBuffer, bytesRead);
                long elapsed = System.nanoTime() - started;
                if (!adaptiveBuffer.putPcm16LittleEndian(outputBuffer, bytesRead)) break;
                metrics.blockCompleted(elapsed, bytesRead * 1_000.0 /
                        (format.bytesPerFrame() * format.sampleRate()));
            }
        } catch (Throwable exception) {
            if (!stopRequested) captureFailure.compareAndSet(null, exception);
        } finally {
            adaptiveBuffer.close();
        }
    }

    private int readPlaybackBlock(AdaptivePcmBuffer adaptiveBuffer, byte[] buffer, int targetBufferFrames,
                                  AtomicReference<Throwable> captureFailure) throws InterruptedException {
        int length = adaptiveBuffer.readPcm16LittleEndian(buffer, format.blockFrames(), targetBufferFrames);
        metrics.setSynchronization(adaptiveBuffer.bufferedFrames() * 1_000.0 / format.sampleRate(),
                (adaptiveBuffer.playbackRate() - 1.0) * 1_000_000.0);
        if (length < 0 && !stopRequested) {
            throwCaptureFailure(captureFailure);
        }
        return length;
    }

    private static void throwCaptureFailure(AtomicReference<Throwable> captureFailure) {
        Throwable failure = captureFailure.get();
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        if (failure != null) throw new IllegalStateException("Falló la captura de audio", failure);
        throw new IllegalStateException("La captura de audio terminó inesperadamente");
    }

    private static void joinCapture(Thread thread) {
        try {
            thread.join(1_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
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
        Thread capture = captureWorker;
        if (capture != null) capture.interrupt();
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
