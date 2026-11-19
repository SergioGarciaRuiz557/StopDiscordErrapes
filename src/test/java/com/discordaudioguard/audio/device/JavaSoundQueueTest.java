package com.discordaudioguard.audio.device;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.lang.reflect.Proxy;
import static org.assertj.core.api.Assertions.assertThat;

class JavaSoundQueueTest {
    @Test void inputAvailabilityIsQueuedAudioButOutputAvailabilityIsFreeSpace() {
        JavaSoundAudioInput input = new JavaSoundAudioInput(line(TargetDataLine.class));
        JavaSoundAudioOutput output = new JavaSoundAudioOutput(line(SourceDataLine.class));
        assertThat(input.queuedBytes()).isEqualTo(1024);
        assertThat(output.queuedBytes()).isEqualTo(6144 - 1024);
        assertThat(input.framePosition()).isEqualTo(4_000_000_000L);
        assertThat(output.framePosition()).isEqualTo(4_000_000_000L);
    }

    private static <T> T line(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "available" -> 1024;
                case "getBufferSize" -> 6144;
                case "getLongFramePosition" -> 4_000_000_000L;
                default -> throw new AssertionError("Unexpected line call: " + method.getName());
            };
        }));
    }
}
