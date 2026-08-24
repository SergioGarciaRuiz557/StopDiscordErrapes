package com.discordaudioguard.application;

import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import com.discordaudioguard.audio.engine.AudioEngineState;

import java.util.List;

/**
 * Immutable snapshot of the state delivered by the application layer to the UI.
 *
 * <p>It groups the engine's operational state, a readable message, and the most
 * recently discovered device lists. Because it is immutable, an instance can be
 * published from the engine thread and later consumed on the JavaFX thread without
 * its contents changing in transit.</p>
 *
 * @param engineState current audio-engine state
 * @param statusMessage user-facing explanation of the state or latest operation
 * @param inputDevices devices capable of serving as input
 * @param outputDevices devices capable of serving as output
 */
public record ApplicationState(AudioEngineState engineState, String statusMessage,
                               List<AudioDeviceDescriptor> inputDevices,
                               List<AudioDeviceDescriptor> outputDevices) {
    /**
     * Creates the state used before the first device scan.
     *
     * @return stopped state with a ready message and empty device lists
     */
    public static ApplicationState initial() {
        return new ApplicationState(AudioEngineState.STOPPED, "Listo", List.of(), List.of());
    }
}
