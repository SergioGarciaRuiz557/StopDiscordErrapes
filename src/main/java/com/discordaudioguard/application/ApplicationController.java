package com.discordaudioguard.application;

import com.discordaudioguard.audio.device.*;
import com.discordaudioguard.audio.engine.*;
import com.discordaudioguard.audio.processing.*;
import com.discordaudioguard.config.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ApplicationController implements AutoCloseable {
    private final ConfigurationRepository configurationRepository;
    private final JavaSoundAudioBackend backend;
    private final AudioMetrics metrics = new AudioMetrics();
    private final DynamicsProcessor processor;
    private final AudioEngine engine;
    private final CopyOnWriteArrayList<Consumer<ApplicationState>> stateListeners = new CopyOnWriteArrayList<>();
    private volatile ApplicationConfiguration configuration;
    private volatile ApplicationState state = ApplicationState.initial();

    public ApplicationController() { this(new JsonConfigurationRepository(), new JavaSoundAudioBackend()); }

    ApplicationController(ConfigurationRepository repository, JavaSoundAudioBackend backend) {
        this.configurationRepository = repository; this.backend = backend;
        this.configuration = repository.load();
        processor = new DynamicsProcessor(configuration.audioFormat().sampleRate(), configuration.audioFormat().blockFrames(), configuration.processing());
        engine = new AudioEngine(backend, configuration.audioFormat(), processor, metrics, configuration.bufferBlocks());
        engine.addStateListener((engineState, message) -> {
            state = new ApplicationState(engineState, message, state.inputDevices(), state.outputDevices());
            notifyState();
        });
    }

    public void refreshDevices() {
        List<AudioDeviceDescriptor> all = backend.scanDevices(configuration.audioFormat());
        List<AudioDeviceDescriptor> inputs = all.stream().filter(AudioDeviceDescriptor::inputSupported).toList();
        List<AudioDeviceDescriptor> outputs = all.stream().filter(AudioDeviceDescriptor::outputSupported).toList();
        String message = all.isEmpty() ? "No hay dispositivos de audio disponibles" : "Se encontraron " + inputs.size() + " entradas y " + outputs.size() + " salidas";
        state = new ApplicationState(engine.state(), message, inputs, outputs);
        notifyState();
    }

    public void start(AudioDeviceDescriptor input, AudioDeviceDescriptor output) {
        if (input == null) throw new IllegalArgumentException("Selecciona un dispositivo de entrada");
        if (output == null) throw new IllegalArgumentException("Selecciona un dispositivo de salida");
        configuration = configuration.withDevices(input.id(), output.id());
        configurationRepository.save(configuration);
        engine.start(input, output);
    }

    public void stop() { engine.stop(); }
    public void updateParameters(ProcessingParameters parameters) {
        processor.updateParameters(parameters);
        configuration = configuration.withProcessing(parameters);
    }
    public void resetParameters() { updateParameters(ProcessingParameters.DEFAULT); }
    public AudioMetrics.Snapshot metricsSnapshot() { return metrics.snapshot(); }
    public ApplicationConfiguration configuration() { return configuration; }
    public ApplicationState state() { return state; }
    public void addStateListener(Consumer<ApplicationState> listener) { stateListeners.add(listener); }
    public void saveWindow(ApplicationConfiguration.WindowConfiguration window) {
        configuration = configuration.withWindow(window);
        configurationRepository.save(configuration);
    }
    public void markIntroductionShown() {
        configuration = configuration.withFirstRun(false);
        configurationRepository.save(configuration);
    }
    private void notifyState() { for (Consumer<ApplicationState> listener : stateListeners) listener.accept(state); }
    @Override public void close() { engine.close(); configurationRepository.save(configuration); }
}
