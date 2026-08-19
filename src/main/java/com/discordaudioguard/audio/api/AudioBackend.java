package com.discordaudioguard.audio.api;

import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;

import java.util.List;

/** Abstraction boundary that permits replacing Java Sound with WASAPI in a future release. */
public interface AudioBackend {
    List<AudioDeviceDescriptor> scanDevices(AudioFormatConfiguration format);
    AudioInput openInput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks);
    AudioOutput openOutput(AudioDeviceDescriptor device, AudioFormatConfiguration format, int bufferBlocks);
}
