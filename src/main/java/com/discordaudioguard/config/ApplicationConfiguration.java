package com.discordaudioguard.config;

import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import com.discordaudioguard.audio.processing.ProcessingParameters;

/**
 * Immutable, serializable root configuration for Discord Audio Guard.
 *
 * <p>Device identifiers may be {@code null} until the user selects a route. The other
 * components form a complete snapshot replaced through {@code with...} methods, which
 * simplifies both cross-thread publication and JSON persistence.</p>
 *
 * @param inputDeviceId stable identifier of the selected input, or {@code null}
 * @param outputDeviceId stable identifier of the selected output, or {@code null}
 * @param processing compressor, limiter, bypass, and final-gain parameters
 * @param audioFormat PCM format and processing block size
 * @param bufferBlocks requested number of buffer blocks
 * @param window remembered main-window geometry
 * @param darkTheme theme preference reserved for the UI
 * @param firstRun whether the getting-started guide should be shown
 */
public record ApplicationConfiguration(String inputDeviceId, String outputDeviceId,
                                       ProcessingParameters processing, AudioFormatConfiguration audioFormat,
                                       int bufferBlocks, WindowConfiguration window, boolean darkTheme,
                                       boolean firstRun) {
    /**
     * Creates preferences for a new installation.
     *
     * @return recommended safe configuration
     */
    public static ApplicationConfiguration defaults() {
        return new ApplicationConfiguration(null, null, ProcessingParameters.DEFAULT,
                AudioFormatConfiguration.DEFAULT, 4, WindowConfiguration.DEFAULT, false, true);
    }

    /**
     * Validates invariants that must also hold when JSON is deserialized.
     * Missing window geometry is replaced with the default to tolerate files created
     * by older versions.
     */
    public ApplicationConfiguration {
        if (processing == null || audioFormat == null) throw new IllegalArgumentException("Configuración de audio incompleta");
        if (bufferBlocks < 2 || bufferBlocks > 32) throw new IllegalArgumentException("bufferBlocks debe estar entre 2 y 32");
        if (window == null) window = WindowConfiguration.DEFAULT;
    }

    /**
     * Replaces the device route while preserving all other preferences.
     *
     * @param input input identifier, or {@code null}
     * @param output output identifier, or {@code null}
     * @return copy with the specified identifiers
     */
    public ApplicationConfiguration withDevices(String input, String output) {
        return new ApplicationConfiguration(input, output, processing, audioFormat, bufferBlocks, window, darkTheme, firstRun);
    }
    /**
     * Replaces the dynamics parameters.
     *
     * @param value new DSP parameters
     * @return copy with the specified parameter set
     */
    public ApplicationConfiguration withProcessing(ProcessingParameters value) {
        return new ApplicationConfiguration(inputDeviceId, outputDeviceId, value, audioFormat, bufferBlocks, window, darkTheme, firstRun);
    }
    /**
     * Replaces the remembered window geometry.
     *
     * @param value new geometry
     * @return copy with the specified geometry
     */
    public ApplicationConfiguration withWindow(WindowConfiguration value) {
        return new ApplicationConfiguration(inputDeviceId, outputDeviceId, processing, audioFormat, bufferBlocks, value, darkTheme, firstRun);
    }
    /**
     * Changes whether the introduction should be shown.
     *
     * @param value new first-run flag
     * @return copy with the updated flag
     */
    public ApplicationConfiguration withFirstRun(boolean value) {
        return new ApplicationConfiguration(inputDeviceId, outputDeviceId, processing, audioFormat, bufferBlocks, window, darkTheme, value);
    }

    /**
     * Persisted geometry of the main window.
     *
     * @param x horizontal coordinate; {@link Double#NaN} delegates positioning to the system
     * @param y vertical coordinate; {@link Double#NaN} delegates positioning to the system
     * @param width width in logical pixels
     * @param height height in logical pixels
     * @param maximized whether the window was maximized
     */
    public record WindowConfiguration(double x, double y, double width, double height, boolean maximized) {
        /** Initial geometry when no previous session exists. */
        public static final WindowConfiguration DEFAULT = new WindowConfiguration(Double.NaN, Double.NaN, 1080, 760, false);

        /** Replaces unreasonable or incompatible dimensions with safe values. */
        public WindowConfiguration {
            if (!Double.isNaN(width) && (width < 800 || width > 5000)) width = 1080;
            if (!Double.isNaN(height) && (height < 600 || height > 5000)) height = 760;
        }
    }
}
