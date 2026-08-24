package com.discordaudioguard.audio.device;

import com.discordaudioguard.audio.api.AudioOutput;
import javax.sound.sampled.SourceDataLine;

/** Adapts the {@link AudioOutput} contract to a {@link SourceDataLine}. */
public final class JavaSoundAudioOutput implements AudioOutput {
    /** Open playback line owned by this adapter. */
    private final SourceDataLine line;

    /**
     * Takes ownership of a line prepared by the backend.
     *
     * @param line already-open line to be controlled and closed by this instance
     */
    public JavaSoundAudioOutput(SourceDataLine line) { this.line = line; }

    /** Starts consuming audio that may already have been preloaded. */
    @Override public void start() { line.start(); }

    /** Delegates the potentially blocking write to Java Sound. */
    @Override public int write(byte[] buffer, int offset, int length) { return line.write(buffer, offset, length); }

    /** Discards pending audio before stopping playback. */
    @Override public void stop() { if (line.isRunning()) { line.flush(); line.stop(); } }

    /** @return capacity configured by the backend, in bytes */
    @Override public int bufferSizeBytes() { return line.getBufferSize(); }

    /** Releases the playback line. */
    @Override public void close() { line.close(); }
}
