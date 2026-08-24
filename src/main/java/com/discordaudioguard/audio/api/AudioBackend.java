package com.discordaudioguard.audio.api;

import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;

import java.util.List;

/**
 * Boundary between the platform-independent engine and a concrete audio system.
 *
 * <p>A backend enumerates endpoints and opens streams compatible with the requested
 * format. This abstraction lets the engine use Java Sound today and later move to
 * WASAPI or another API without changing the DSP or main loop.</p>
 */
public interface AudioBackend {
    /**
     * Enumerates input and output devices and checks their compatibility.
     *
     * @param format format intended for opening
     * @return immutable snapshot of visible devices
     */
    List<AudioDeviceDescriptor> scanDevices(AudioFormatConfiguration format);

    /**
     * Opens, but does not start, an input on the requested device.
     *
     * @param device descriptor obtained from {@link #scanDevices(AudioFormatConfiguration)}
     * @param format required PCM format
     * @param bufferBlocks suggested capacity in processing blocks
     * @return open input whose ownership passes to the caller
     * @throws RuntimeException if the device disappeared, is busy, or rejects the format
     */
    AudioInput openInput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks);

    /**
     * Opens, but does not start, an output on the requested device.
     *
     * @param device descriptor obtained from {@link #scanDevices(AudioFormatConfiguration)}
     * @param format required PCM format
     * @param bufferBlocks suggested capacity in processing blocks
     * @return open output whose ownership passes to the caller
     * @throws RuntimeException if the device disappeared, is busy, or rejects the format
     */
    AudioOutput openOutput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks);
}
