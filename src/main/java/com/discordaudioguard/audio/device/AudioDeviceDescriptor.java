package com.discordaudioguard.audio.device;

/**
 * Immutable description of an audio mixer visible to the application.
 *
 * <p>The identifier derives from name, vendor, and description rather than enumeration
 * position, which may change between runs. Input and output capabilities reflect the
 * mixer's general lines; format compatibility indicates whether at least one direction
 * accepts the exact requested format.</p>
 *
 * @param id stable identifier generated for persisted selections
 * @param name short name supplied by the driver
 * @param description human-readable device description
 * @param vendor vendor reported by Java Sound
 * @param version provider or driver version
 * @param inputSupported whether the mixer exposes any capture line
 * @param outputSupported whether the mixer exposes any playback line
 * @param requestedFormatSupported whether it accepts the format used during scanning
 */
public record AudioDeviceDescriptor(String id, String name, String description, String vendor,
                                    String version, boolean inputSupported, boolean outputSupported,
                                    boolean requestedFormatSupported) {
    /** @return compact text displayed by JavaFX selectors */
    @Override public String toString() {
        return name + (description == null || description.isBlank() ? "" : " — " + description);
    }
}
