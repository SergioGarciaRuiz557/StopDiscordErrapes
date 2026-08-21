package com.discordaudioguard.audio.device;

public record AudioDeviceDescriptor(String id, String name, String description, String vendor,
                                    String version, boolean inputSupported, boolean outputSupported,
                                    boolean requestedFormatSupported) {
    @Override public String toString() {
        return name + (description == null || description.isBlank() ? "" : " — " + description);
    }
}
