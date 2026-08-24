package com.discordaudioguard.audio.api;

/**
 * PCM capture stream opened by an {@link AudioBackend}.
 *
 * <p>Its semantics mirror a blocking audio line: {@link #read(byte[], int, int)} may
 * wait for data. The owner must stop and close the stream to unblock pending reads
 * during shutdown.</p>
 */
public interface AudioInput extends AutoCloseable {
    /** Starts delivering audio from the device. */
    void start();

    /**
     * Reads at most {@code length} PCM bytes into a destination region.
     *
     * @param buffer destination for captured bytes
     * @param offset starting position in the destination
     * @param length maximum requested byte count
     * @return bytes read, zero when no progress occurred, or a negative value at end of stream
     */
    int read(byte[] buffer, int offset, int length);

    /** Stops capture; the operation must be safe when already stopped. */
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
