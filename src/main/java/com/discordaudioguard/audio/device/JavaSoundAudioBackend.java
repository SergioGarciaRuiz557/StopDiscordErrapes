package com.discordaudioguard.audio.device;

import com.discordaudioguard.audio.api.AudioBackend;
import com.discordaudioguard.audio.api.AudioInput;
import com.discordaudioguard.audio.api.AudioOutput;
import com.discordaudioguard.audio.format.AudioFormatConfiguration;

import javax.sound.sampled.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * {@link AudioBackend} adapter based on the standard Java Sound API.
 *
 * <p>It resolves each descriptor against mixers present at open time, checks direction
 * and format, reserves the native buffer, and returns wrappers that hide
 * {@link TargetDataLine} and {@link SourceDataLine} from the engine.</p>
 */
public final class JavaSoundAudioBackend implements AudioBackend {
    /**
     * Empirical minimum reserve for scheduler jitter and drivers that produce
     * discontinuities with undersized buffers.
     */
    private static final int MINIMUM_STABLE_BUFFER_BLOCKS = 12;
    /** Component responsible for fault-tolerant enumeration. */
    private final AudioDeviceScanner scanner = new AudioDeviceScanner();

    /** Creates the adapter with its own Java Sound enumerator. */
    public JavaSoundAudioBackend() {
        // The backend does not open lines until the engine requests an endpoint.
    }

    /** {@inheritDoc} */
    @Override public List<AudioDeviceDescriptor> scanDevices(AudioFormatConfiguration format) { return scanner.scan(format); }

    /**
     * {@inheritDoc}
     *
     * <p>The line is returned open but stopped; the engine decides when to start it.</p>
     */
    @Override
    public AudioInput openInput(AudioDeviceDescriptor device, AudioFormatConfiguration configuration, int bufferBlocks) {
        if (!device.inputSupported()) throw new AudioDeviceException("El dispositivo de entrada no está disponible: " + device.name());
        AudioFormat format = configuration.toJavaSoundFormat();
        try {
            Mixer mixer = findMixer(device);
            DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
            if (!mixer.isLineSupported(lineInfo)) throw unsupported(device, configuration);
            TargetDataLine line = (TargetDataLine) mixer.getLine(lineInfo);
            // Java Sound expresses capacity in bytes, not frames or processing blocks.
            line.open(format, configuration.blockBytes() * Math.max(MINIMUM_STABLE_BUFFER_BLOCKS, bufferBlocks));
            return new JavaSoundAudioInput(line);
        } catch (LineUnavailableException exception) {
            throw new AudioDeviceException("No se pudo abrir la línea de entrada “" + device.name() + "”: " + exception.getMessage(), exception);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The output accepts preloading before start, a feature used by the engine.</p>
     */
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

    /**
     * Recomputes identifiers over the current inventory to detect disconnections
     * between the UI scan and the user pressing Start.
     */
    private Mixer findMixer(AudioDeviceDescriptor descriptor) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            String key = info.getName() + '\u0000' + info.getVendor() + '\u0000' + info.getDescription();
            String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
            if (id.equals(descriptor.id())) return AudioSystem.getMixer(info);
        }
        throw new AudioDeviceException("El dispositivo ya no existe o fue desconectado: " + descriptor.name());
    }

    /** Builds a readable error containing the rejected device and format. */
    private static AudioDeviceException unsupported(AudioDeviceDescriptor device, AudioFormatConfiguration format) {
        return new AudioDeviceException("El formato solicitado no está soportado por “" + device.name() + "”: " +
                (int) format.sampleRate() + " Hz, " + format.sampleSizeBits() + " bits, estéreo, little endian");
    }

    /**
     * Device error suitable for direct display to the user.
     * It wraps both logical incompatibilities and native open failures.
     */
    public static final class AudioDeviceException extends RuntimeException {
        /**
         * Creates an error without an associated native cause.
         *
         * @param message human-readable failure explanation
         */
        public AudioDeviceException(String message) { super(message); }

        /**
         * Creates an error while retaining the original exception.
         *
         * @param message human-readable failure explanation
         * @param cause original Java Sound exception
         */
        public AudioDeviceException(String message, Throwable cause) { super(message, cause); }
    }
}
