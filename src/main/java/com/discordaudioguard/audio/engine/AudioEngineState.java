package com.discordaudioguard.audio.engine;

/** Observable lifecycle states of {@link AudioEngine}. */
public enum AudioEngineState {
    /** No devices are open and no threads are active. */
    STOPPED("Detenido"),
    /** The worker thread is opening and preloading devices. */
    STARTING("Iniciando"),
    /** Capture, DSP, and playback are active. */
    RUNNING("Funcionando"),
    /** Shutdown was requested and resources are being unblocked. */
    STOPPING("Deteniendo"),
    /** The stream ended because of an unsolicited error. */
    ERROR("Error");

    /** Localized label presented in the status bar. */
    private final String displayName;

    /** @param displayName localized label for the UI */
    AudioEngineState(String displayName) { this.displayName = displayName; }

    /**
     * Returns the text intended for the UI.
     *
     * @return localized state label
     */
    public String displayName() { return displayName; }
}
