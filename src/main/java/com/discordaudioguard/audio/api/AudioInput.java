package com.discordaudioguard.audio.api;

public interface AudioInput extends AutoCloseable {
    void start();
    int read(byte[] buffer, int offset, int length);
    void stop();
    int bufferSizeBytes();
    @Override void close();
}
