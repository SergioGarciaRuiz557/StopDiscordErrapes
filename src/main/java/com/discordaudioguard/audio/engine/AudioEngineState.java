package com.discordaudioguard.audio.engine;

public enum AudioEngineState {
    STOPPED("Detenido"), STARTING("Iniciando"), RUNNING("Funcionando"), STOPPING("Deteniendo"), ERROR("Error");
    private final String displayName;
    AudioEngineState(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
