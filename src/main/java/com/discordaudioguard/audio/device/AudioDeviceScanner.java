package com.discordaudioguard.audio.device;

import com.discordaudioguard.audio.format.AudioFormatConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AudioDeviceScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioDeviceScanner.class);

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
