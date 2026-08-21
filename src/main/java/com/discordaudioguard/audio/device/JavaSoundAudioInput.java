package com.discordaudioguard.audio.device;

import com.discordaudioguard.audio.api.AudioInput;
import javax.sound.sampled.TargetDataLine;

public final class JavaSoundAudioInput implements AudioInput {
    private final TargetDataLine line;
    public JavaSoundAudioInput(TargetDataLine line) { this.line = line; }
    @Override public void start() { line.start(); }
    @Override public int read(byte[] buffer, int offset, int length) { return line.read(buffer, offset, length); }
    @Override public void stop() { if (line.isRunning()) line.stop(); }
    @Override public int bufferSizeBytes() { return line.getBufferSize(); }
    @Override public void close() { line.close(); }
}
