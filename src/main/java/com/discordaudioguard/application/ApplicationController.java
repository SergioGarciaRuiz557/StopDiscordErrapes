package com.discordaudioguard.application;

import com.discordaudioguard.audio.device.*;
import com.discordaudioguard.audio.engine.*;
import com.discordaudioguard.audio.processing.*;
import com.discordaudioguard.config.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Use-case facade and primary application coordinator.
 *
 * <p>This class separates JavaFX from persistence and audio details. It maintains the
 * active configuration, discovers devices, configures the DSP chain, and translates
 * {@link AudioEngine} changes into observable {@link ApplicationState} objects. The
 * graphical interface only needs to communicate with this controller.</p>
 *
 * <p>Engine events may arrive from an audio thread. State and configuration are
 * therefore {@code volatile}, while observers are stored in a
 * {@link CopyOnWriteArrayList}; registering or iterating listeners does not block the
 * audio path. Consumers must transfer thread-affine work to the appropriate thread,
 * as JavaFX does with {@code Platform.runLater}.</p>
 */
public final class ApplicationController implements AutoCloseable {
    /** Store used to load and persist preferences. */
    private final ConfigurationRepository configurationRepository;
    /** Java Sound implementation that enumerates and opens devices. */
    private final JavaSoundAudioBackend backend;
    /** Shared telemetry container written by audio and read by the UI. */
    private final AudioMetrics metrics = new AudioMetrics();
    /** DSP chain reused throughout the session. */
    private final DynamicsProcessor processor;
    /** Engine responsible for the capture-process-playback cycle. */
    private final AudioEngine engine;
    /** Subscribers that receive every published state snapshot. */
    private final CopyOnWriteArrayList<Consumer<ApplicationState>> stateListeners = new CopyOnWriteArrayList<>();
    /** Effective configuration, atomically replaced with new immutable copies. */
    private volatile ApplicationConfiguration configuration;
    /** Most recently published application state. */
    private volatile ApplicationState state = ApplicationState.initial();

    /**
     * Builds the controller with JSON persistence in the user profile and device
     * access through Java Sound.
     */
    public ApplicationController() { this(new JsonConfigurationRepository(), new JavaSoundAudioBackend()); }

    /**
     * Package-level injection point used by tests.
     *
     * @param repository repository from which configuration is loaded and to which it is saved
     * @param backend backend that provides audio devices
     */
    ApplicationController(ConfigurationRepository repository, JavaSoundAudioBackend backend) {
        this.configurationRepository = repository; this.backend = backend;
        this.configuration = repository.load();
        processor = new DynamicsProcessor(configuration.audioFormat().sampleRate(), configuration.audioFormat().blockFrames(), configuration.processing());
        engine = new AudioEngine(backend, configuration.audioFormat(), processor, metrics, configuration.bufferBlocks());
        // Preserve the latest scan lists when only the engine state changes.
        engine.addStateListener((engineState, message) -> {
            state = new ApplicationState(engineState, message, state.inputDevices(), state.outputDevices());
            notifyState();
        });
    }

    /**
     * Re-enumerates devices and publishes independent input and output lists.
     * A duplex device may appear in both lists.
     */
    public void refreshDevices() {
        List<AudioDeviceDescriptor> all = backend.scanDevices(configuration.audioFormat());
        List<AudioDeviceDescriptor> inputs = all.stream().filter(AudioDeviceDescriptor::inputSupported).toList();
        List<AudioDeviceDescriptor> outputs = all.stream().filter(AudioDeviceDescriptor::outputSupported).toList();
        String message = all.isEmpty() ? "No hay dispositivos de audio disponibles" : "Se encontraron " + inputs.size() + " entradas y " + outputs.size() + " salidas";
        state = new ApplicationState(engine.state(), message, inputs, outputs);
        notifyState();
    }

    /**
     * Saves the selection and starts the audio stream in the background.
     *
     * @param input device from which PCM is captured
     * @param output device through which the protected result is played
     * @throws IllegalArgumentException if either selection is missing
     * @throws IllegalStateException if the engine is already active or changing state
     */
    public void start(AudioDeviceDescriptor input, AudioDeviceDescriptor output) {
        if (input == null) throw new IllegalArgumentException("Selecciona un dispositivo de entrada");
        if (output == null) throw new IllegalArgumentException("Selecciona un dispositivo de salida");
        configuration = configuration.withDevices(input.id(), output.id());
        configurationRepository.save(configuration);
        engine.start(input, output);
    }

    /** Requests a safe asynchronous engine stop. */
    public void stop() { engine.stop(); }

    /**
     * Publishes new parameters to the DSP thread and updates the in-memory copy.
     * Final persistence occurs on close, except for changes explicitly saved by other methods.
     *
     * @param parameters complete, validated set of DSP controls
     */
    public void updateParameters(ProcessingParameters parameters) {
        processor.updateParameters(parameters);
        configuration = configuration.withProcessing(parameters);
    }
    /** Restores the application's recommended DSP parameters. */
    public void resetParameters() { updateParameters(ProcessingParameters.DEFAULT); }

    /**
     * Copies telemetry without blocking audio.
     *
     * @return snapshot of the most recent metrics
     */
    public AudioMetrics.Snapshot metricsSnapshot() { return metrics.snapshot(); }

    /**
     * Returns the in-memory preferences.
     *
     * @return currently active immutable configuration
     */
    public ApplicationConfiguration configuration() { return configuration; }

    /**
     * Returns observable state without waiting for another notification.
     *
     * @return most recently published state snapshot
     */
    public ApplicationState state() { return state; }

    /**
     * Registers an observer for subsequent changes. Registration does not immediately
     * emit the current state.
     *
     * @param listener consumer invoked on the thread that originates each change
     */
    public void addStateListener(Consumer<ApplicationState> listener) { stateListeners.add(listener); }

    /**
     * Persists the main window's geometry and maximized state.
     *
     * @param window new window geometry
     */
    public void saveWindow(ApplicationConfiguration.WindowConfiguration window) {
        configuration = configuration.withWindow(window);
        configurationRepository.save(configuration);
    }
    /** Marks and persists that the getting-started dialog has been shown. */
    public void markIntroductionShown() {
        configuration = configuration.withFirstRun(false);
        configurationRepository.save(configuration);
    }
    /** Delivers the current snapshot to every registered listener. */
    private void notifyState() { for (Consumer<ApplicationState> listener : stateListeners) listener.accept(state); }

    /** Stops the engine and persists the latest complete configuration. */
    @Override public void close() { engine.close(); configurationRepository.save(configuration); }
}
