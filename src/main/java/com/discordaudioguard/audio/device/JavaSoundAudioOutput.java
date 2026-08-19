package com.discordaudioguard.audio.device;

import com.discordaudioguard.audio.api.AudioOutput;
import javax.sound.sampled.SourceDataLine;

public final class JavaSoundAudioOutput implements AudioOutput {
    private final SourceDataLine line;
    public JavaSoundAudioOutput(SourceDataLine line) { this.line = line; }
    @Override public void start() { line.start(); }
    @Override public int write(byte[] buffer, int offset, int length) { return line.write(buffer, offset, length); }
    @Override public void stop() { if (line.isRunning()) { line.flush(); line.stop(); } }
    @Override public int bufferSizeBytes() { return line.getBufferSize(); }
    @Override public void close() { line.close(); }
}
