package com.discordaudioguard.application;

import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import com.discordaudioguard.audio.engine.AudioEngineState;

import java.util.List;

public record ApplicationState(AudioEngineState engineState, String statusMessage,
                               List<AudioDeviceDescriptor> inputDevices,
                               List<AudioDeviceDescriptor> outputDevices) {
    public static ApplicationState initial() {
        return new ApplicationState(AudioEngineState.STOPPED, "Listo", List.of(), List.of());
    }
}
