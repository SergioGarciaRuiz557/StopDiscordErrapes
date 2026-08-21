package com.discordaudioguard.audio.device;

import com.discordaudioguard.audio.api.AudioBackend;
import com.discordaudioguard.audio.api.AudioInput;
import com.discordaudioguard.audio.api.AudioOutput;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;

import javax.sound.sampled.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class JavaSoundAudioBackend implements AudioBackend {
    private static final int MINIMUM_STABLE_BUFFER_BLOCKS = 12;
    private final AudioDeviceScanner scanner = new AudioDeviceScanner();

    @Override public List<AudioDeviceDescriptor> scanDevices(AudioFormatConfiguration format) { return scanner.scan(format); }

    @Override
    public AudioInput openInput(AudioDeviceDescriptor device, AudioFormatConfiguration configuration, int bufferBlocks) {
        if (!device.inputSupported()) throw new AudioDeviceException("El dispositivo de entrada no está disponible: " + device.name());
        AudioFormat format = configuration.toJavaSoundFormat();
        try {
            Mixer mixer = findMixer(device);
            DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
            if (!mixer.isLineSupported(lineInfo)) throw unsupported(device, configuration);
            TargetDataLine line = (TargetDataLine) mixer.getLine(lineInfo);
            line.open(format, configuration.blockBytes() * Math.max(MINIMUM_STABLE_BUFFER_BLOCKS, bufferBlocks));
            return new JavaSoundAudioInput(line);
        } catch (LineUnavailableException exception) {
            throw new AudioDeviceException("No se pudo abrir la línea de entrada “" + device.name() + "”: " + exception.getMessage(), exception);
        }
    }

    @Override
    public AudioOutput openOutput(AudioDeviceDescriptor device, AudioFormatConfiguration configuration, int bufferBlocks) {
        if (!device.outputSupported()) throw new AudioDeviceException("El dispositivo de salida no está disponible: " + device.name());
        AudioFormat format = configuration.toJavaSoundFormat();
        try {
            Mixer mixer = findMixer(device);
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
            if (!mixer.isLineSupported(lineInfo)) throw unsupported(device, configuration);
            SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
            line.open(format, configuration.blockBytes() * Math.max(MINIMUM_STABLE_BUFFER_BLOCKS, bufferBlocks));
            return new JavaSoundAudioOutput(line);
        } catch (LineUnavailableException exception) {
            throw new AudioDeviceException("No se pudo abrir la línea de salida “" + device.name() + "”: " + exception.getMessage(), exception);
        }
    }

    private Mixer findMixer(AudioDeviceDescriptor descriptor) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            String key = info.getName() + '\u0000' + info.getVendor() + '\u0000' + info.getDescription();
            String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
            if (id.equals(descriptor.id())) return AudioSystem.getMixer(info);
        }
        throw new AudioDeviceException("El dispositivo ya no existe o fue desconectado: " + descriptor.name());
    }

    private static AudioDeviceException unsupported(AudioDeviceDescriptor device, AudioFormatConfiguration format) {
        return new AudioDeviceException("El formato solicitado no está soportado por “" + device.name() + "”: " +
                (int) format.sampleRate() + " Hz, " + format.sampleSizeBits() + " bits, estéreo, little endian");
    }

    public static final class AudioDeviceException extends RuntimeException {
        public AudioDeviceException(String message) { super(message); }
        public AudioDeviceException(String message, Throwable cause) { super(message, cause); }
    }
}
