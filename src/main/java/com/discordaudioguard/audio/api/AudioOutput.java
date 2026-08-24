package com.discordaudioguard.audio.api;

/**
 * PCM playback stream opened by an {@link AudioBackend}.
 *
 * <p>Writes may be partial or blocking. The engine remains responsible for retrying
 * until a block is complete and for closing the output to unblock operations during shutdown.</p>
 */
public interface AudioOutput extends AutoCloseable {
    /** Starts the device playback clock. */
    void start();

    /**
     * Delivers PCM bytes from a buffer region.
     *
     * @param buffer encoded-audio source
     * @param offset first byte to write
     * @param length maximum requested count
     * @return number of bytes accepted by the device
     */
    int write(byte[] buffer, int offset, int length);

    /** Stops playback and, when appropriate, discards queued audio. */
    void stop();

    /**
     * Returns the buffer capacity actually granted by the driver.
     *
     * @return native buffer capacity in bytes
     */
    int bufferSizeBytes();

    /** Releases the line and all associated native resources. */
    @Override void close();
}
