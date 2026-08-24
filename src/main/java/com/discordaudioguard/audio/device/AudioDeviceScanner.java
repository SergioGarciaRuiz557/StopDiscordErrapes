package com.discordaudioguard.audio.device;

import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enumerator of Java Sound mixers and their relevant capabilities.
 *
 * <p>A faulty driver must not hide other devices: each mixer is inspected in isolation,
 * and runtime exceptions are logged before scanning continues. The result excludes
 * mixers without usable input or output lines.</p>
 */
public final class AudioDeviceScanner {
    /** Log for devices that could not be inspected. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioDeviceScanner.class);

    /** Creates a stateless enumerator; every scan queries the system again. */
    public AudioDeviceScanner() {
        // Do not retain mixers because they may appear or disappear between scans.
    }

    /**
     * Captures a snapshot of available mixers and tests the specified format.
     *
     * @param configuration format whose compatibility is checked
     * @return immutable descriptor list in system enumeration order
     */
    public List<AudioDeviceDescriptor> scan(AudioFormatConfiguration configuration) {
        AudioFormat format = configuration.toJavaSoundFormat();
        DataLine.Info inputInfo = new DataLine.Info(TargetDataLine.class, format);
        DataLine.Info outputInfo = new DataLine.Info(SourceDataLine.class, format);
        List<AudioDeviceDescriptor> devices = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                boolean input = mixer.getTargetLineInfo().length > 0;
                boolean output = mixer.getSourceLineInfo().length > 0;
                if (!input && !output) continue;
                boolean formatSupported = (input && mixer.isLineSupported(inputInfo)) || (output && mixer.isLineSupported(outputInfo));
                // Null separators avoid ambiguity when concatenating variable metadata.
                String key = info.getName() + '\u0000' + info.getVendor() + '\u0000' + info.getDescription();
                devices.add(new AudioDeviceDescriptor(
                        UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString(),
                        info.getName(), info.getDescription(), info.getVendor(), info.getVersion(),
                        input, output, formatSupported));
            } catch (RuntimeException exception) {
                LOGGER.warn("No se pudo inspeccionar el mixer {}", info.getName(), exception);
            }
        }
        return List.copyOf(devices);
    }
}
