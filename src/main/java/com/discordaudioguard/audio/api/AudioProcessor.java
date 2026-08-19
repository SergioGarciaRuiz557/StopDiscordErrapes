package com.discordaudioguard.audio.api;

import com.discordaudioguard.audio.engine.AudioMetrics;
import com.discordaudioguard.audio.processing.ProcessingParameters;

public interface AudioProcessor {
    void process(float[] input, float[] output, int frames, AudioMetrics metrics);
    void updateParameters(ProcessingParameters parameters);
    int latencyFrames();
    void reset();
}
