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

/**
 * Concurrent engine that transports audio from one input to one output.
 *
 * <p>The engine owns two daemon threads. The capture thread reads complete blocks,
 * runs {@link AudioPipeline}, and places them in {@link AdaptivePcmBuffer}. The main
 * engine thread preloads and plays blocks while correcting small drift between the
 * physical clocks of both devices. Separating these operations prevents a slow write
 * from blocking capture and causing data loss.</p>
 *
 * <p>The lifecycle is represented by {@link AudioEngineState}. {@link #start} accepts
 * only the terminal STOPPED and ERROR states; {@link #stop} sets a signal, closes lines
 * to unblock I/O, and interrupts both threads. Listeners may run on the calling thread
 * or the engine thread and must not block it.</p>
 */
public final class AudioEngine implements AutoCloseable {
    /** Log for startups and abnormal terminations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioEngine.class);
    /** Consecutive zero-progress reads accepted before assuming disconnection. */
    private static final int MAX_ZERO_READS = 200;
    /** Minimum reserve between capture and playback for absorbing jitter. */
    private static final int MINIMUM_ADAPTIVE_BUFFER_BLOCKS = 8;
    /** Maximum preloaded into the output line to limit added latency. */
    private static final int MAXIMUM_DEVICE_PREFILL_BLOCKS = 11;
    /** Provider of input and output lines. */
    private final AudioBackend backend;
    /** Format that remains invariant throughout the engine's lifetime. */
    private final AudioFormatConfiguration format;
    /** DSP applied on the capture thread. */
    private final AudioProcessor processor;
    /** Telemetry shared with the UI. */
    private final AudioMetrics metrics;
    /** Buffer-reserve preference expressed in blocks. */
    private final int bufferBlocks;
    /** Atomic state used for both transitions and external queries. */
    private final AtomicReference<AudioEngineState> state = new AtomicReference<>(AudioEngineState.STOPPED);
    /** Observers safe for concurrent registration and iteration. */
    private final CopyOnWriteArrayList<BiConsumer<AudioEngineState, String>> listeners = new CopyOnWriteArrayList<>();
    /** Cooperative termination request visible to both threads. */
    private volatile boolean stopRequested;
    /** Thread responsible for opening, preloading, and playback. */
    private volatile Thread worker;
    /** Capture and DSP thread. */
    private volatile Thread captureWorker;
    /** Active input, published so {@link #stop()} can unblock it. */
    private volatile AudioInput activeInput;
    /** Active output, published so {@link #stop()} can unblock it. */
    private volatile AudioOutput activeOutput;

    /**
     * Creates a stopped engine; no devices are opened until {@link #start}.
     *
     * @param backend implementation that opens endpoints
     * @param format format and block size shared by the entire stream
     * @param processor DSP chain compatible with that format
     * @param metrics shared diagnostic collector
     * @param bufferBlocks requested buffer depth
     */
    public AudioEngine(AudioBackend backend, AudioFormatConfiguration format, AudioProcessor processor,
                       AudioMetrics metrics, int bufferBlocks) {
        this.backend = Objects.requireNonNull(backend); this.format = format; this.processor = processor;
        this.metrics = metrics; this.bufferBlocks = bufferBlocks;
    }

    /**
     * Asynchronously starts a new stream between two distinct devices.
     *
     * <p>Synchronization serializes start/stop, while the atomic transition prevents
     * simultaneous starts. Using the same identifier at both ends is rejected to reduce
     * the risk of an audio feedback loop.</p>
     *
     * @param inputDevice capture endpoint
     * @param outputDevice playback endpoint
     * @throws IllegalStateException if the engine was not stopped or in error
     * @throws IllegalArgumentException if both descriptors represent the same device
     */
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

    /**
     * Resource-owning loop: opens lines, creates the buffer, preloads, and plays audio.
     * Any failure not caused by a requested stop is translated into the ERROR state.
     */
    private void runAudio(AudioDeviceDescriptor inputDevice, AudioDeviceDescriptor outputDevice) {
        try (AudioInput input = backend.openInput(inputDevice, format, bufferBlocks);
             AudioOutput output = backend.openOutput(outputDevice, format, bufferBlocks)) {
            activeInput = input; activeOutput = output;
            if (stopRequested) return;
            processor.reset();
            // Match preloading to the capacity actually granted by the driver.
            int outputBufferBlocks = Math.max(1, output.bufferSizeBytes() / format.blockBytes());
            int prefillBlocks = Math.max(1, Math.min(MAXIMUM_DEVICE_PREFILL_BLOCKS, outputBufferBlocks - 1));
            int targetBufferBlocks = Math.max(MINIMUM_ADAPTIVE_BUFFER_BLOCKS, bufferBlocks * 2);
            // Extra margin keeps a brief consumer pause from immediately blocking capture.
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
                // SourceDataLine accepts data before start(): reserves are loaded into
                // both the device and adaptive buffer before its clock is activated.
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

    /**
     * Producer loop: completes input blocks, runs DSP, and queues the results.
     * The first failure is sent to the consumer through {@code captureFailure}; closing
     * the buffer wakes the consumer even when it was waiting for data.
     */
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

    /**
     * Obtains a resampled block, publishes synchronization, and propagates capture failure.
     *
     * @return available bytes, or -1 during a normal stop
     */
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

    /**
     * Preserves original RuntimeExceptions and wraps checked failures or unexpected
     * endings so the owning loop can apply one error policy.
     */
    private static void throwCaptureFailure(AtomicReference<Throwable> captureFailure) {
        Throwable failure = captureFailure.get();
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        if (failure != null) throw new IllegalStateException("Falló la captura de audio", failure);
        throw new IllegalStateException("La captura de audio terminó inesperadamente");
    }

    /** Waits a bounded time for the capture thread and preserves caller interruption. */
    private static void joinCapture(Thread thread) {
        try {
            thread.join(1_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Accumulates partial reads until a block is full or no progress is possible.
     *
     * @return accumulated bytes, zero, or a negative value when returned by the first read
     */
    private int readBlock(AudioInput input, byte[] buffer) {
        int total = 0;
        while (total < buffer.length && !stopRequested) {
            int count = input.read(buffer, total, buffer.length - total);
            if (count <= 0) return total == 0 ? count : total;
            total += count;
        }
        return total;
    }

    /**
     * Retries partial writes until the block is delivered or zero/end is received.
     *
     * @return bytes actually accepted by the output
     */
    private int writeBlock(AudioOutput output, byte[] buffer, int length) {
        int total = 0;
        while (total < length && !stopRequested) {
            int count = output.write(buffer, total, length - total);
            if (count <= 0) break;
            total += count;
        }
        return total;
    }

    /**
     * Requests stream shutdown without waiting for the owning thread to finish.
     *
     * <p>Actively closing lines is essential: read/write may be blocked in native code,
     * and a Java interruption alone does not always wake them.</p>
     */
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

    /** Stops and closes an input during cleanup, ignoring secondary failures. */
    private static void safeStop(AudioInput input) { if (input != null) try { input.stop(); input.close(); } catch (RuntimeException ignored) {} }

    /** Stops and closes an output during cleanup, ignoring secondary failures. */
    private static void safeStop(AudioOutput output) { if (output != null) try { output.stop(); output.close(); } catch (RuntimeException ignored) {} }

    /**
     * Returns the current lifecycle state.
     *
     * @return current atomic state
     */
    public AudioEngineState state() { return state.get(); }

    /**
     * Exposes telemetry for advanced diagnostics.
     *
     * @return metrics collector owned by the engine
     */
    public AudioMetrics metrics() { return metrics; }

    /**
     * Registers an observer for subsequent transitions.
     *
     * @param listener state-and-message consumer that must return quickly
     */
    public void addStateListener(BiConsumer<AudioEngineState, String> listener) { listeners.add(listener); }

    /** Delivers a transition to every observer on the current thread. */
    private void publish(AudioEngineState value, String message) { for (var listener : listeners) listener.accept(value, message); }

    /** Returns useful exception text even when no message was provided. */
    private static String userMessage(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }

    /** Implements idempotent close by requesting stream shutdown. */
    @Override public void close() { stop(); }
}
