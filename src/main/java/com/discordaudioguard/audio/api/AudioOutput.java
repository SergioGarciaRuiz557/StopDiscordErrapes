package com.discordaudioguard.audio.api;

public interface AudioOutput extends AutoCloseable {
    void start();
    int write(byte[] buffer, int offset, int length);
    void stop();
    int bufferSizeBytes();
    @Override void close();
}
