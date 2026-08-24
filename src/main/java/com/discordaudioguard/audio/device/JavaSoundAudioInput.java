package com.discordaudioguard.audio.device;

import com.discordaudioguard.audio.api.AudioInput;
import javax.sound.sampled.TargetDataLine;

/** Adapts the {@link AudioInput} contract to a {@link TargetDataLine}. */
public final class JavaSoundAudioInput implements AudioInput {
    /** Open capture line owned by this adapter. */
    private final TargetDataLine line;

    /**
     * Takes ownership of a line prepared by the backend.
     *
     * @param line already-open line to be controlled and closed by this instance
     */
    public JavaSoundAudioInput(TargetDataLine line) { this.line = line; }

    /** Starts native capture. */
    @Override public void start() { line.start(); }

    /** Delegates the potentially blocking read to Java Sound. */
    @Override public int read(byte[] buffer, int offset, int length) { return line.read(buffer, offset, length); }

    /** Stops the line only when it is running. */
    @Override public void stop() { if (line.isRunning()) line.stop(); }

    /** @return capacity configured by the backend, in bytes */
    @Override public int bufferSizeBytes() { return line.getBufferSize(); }

    /** Releases the capture line. */
    @Override public void close() { line.close(); }
}
