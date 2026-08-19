package com.discordaudioguard.config;

import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import com.discordaudioguard.audio.processing.ProcessingParameters;

public record ApplicationConfiguration(String inputDeviceId, String outputDeviceId,
                                       ProcessingParameters processing, AudioFormatConfiguration audioFormat,
                                       int bufferBlocks, WindowConfiguration window, boolean darkTheme,
                                       boolean firstRun) {
    public static ApplicationConfiguration defaults() {
        return new ApplicationConfiguration(null, null, ProcessingParameters.DEFAULT,
                AudioFormatConfiguration.DEFAULT, 4, WindowConfiguration.DEFAULT, false, true);
    }

    public ApplicationConfiguration {
        if (processing == null || audioFormat == null) throw new IllegalArgumentException("Configuración de audio incompleta");
        if (bufferBlocks < 2 || bufferBlocks > 32) throw new IllegalArgumentException("bufferBlocks debe estar entre 2 y 32");
        if (window == null) window = WindowConfiguration.DEFAULT;
    }

    public ApplicationConfiguration withDevices(String input, String output) {
        return new ApplicationConfiguration(input, output, processing, audioFormat, bufferBlocks, window, darkTheme, firstRun);
    }
    public ApplicationConfiguration withProcessing(ProcessingParameters value) {
        return new ApplicationConfiguration(inputDeviceId, outputDeviceId, value, audioFormat, bufferBlocks, window, darkTheme, firstRun);
    }
    public ApplicationConfiguration withWindow(WindowConfiguration value) {
        return new ApplicationConfiguration(inputDeviceId, outputDeviceId, processing, audioFormat, bufferBlocks, value, darkTheme, firstRun);
    }
    public ApplicationConfiguration withFirstRun(boolean value) {
        return new ApplicationConfiguration(inputDeviceId, outputDeviceId, processing, audioFormat, bufferBlocks, window, darkTheme, value);
    }

    public record WindowConfiguration(double x, double y, double width, double height, boolean maximized) {
        public static final WindowConfiguration DEFAULT = new WindowConfiguration(Double.NaN, Double.NaN, 1080, 760, false);
        public WindowConfiguration {
            if (!Double.isNaN(width) && (width < 800 || width > 5000)) width = 1080;
            if (!Double.isNaN(height) && (height < 600 || height > 5000)) height = 760;
        }
    }
}
