package com.discordaudioguard.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Tests JSON persistence, safe recovery, and preservation of corrupt evidence. */
class JsonConfigurationRepositoryTest {
    /** Isolated directory removed by JUnit after each test. */
    @TempDir Path directory;

    /** Verifies that a complete snapshot survives a JSON round trip. */
    @Test void savesAndLoadsConfiguration() {
        Path file = directory.resolve("config.json"); JsonConfigurationRepository repository = new JsonConfigurationRepository(file);
        ApplicationConfiguration expected = ApplicationConfiguration.defaults().withFirstRun(false);
        repository.save(expected);
        assertThat(repository.load()).isEqualTo(expected);
    }
    /** Verifies fallback to safe values and renaming of the unreadable file. */
    @Test void recoversFromCorruptJsonAndPreservesCopy() throws Exception {
        Path file = directory.resolve("config.json"); Files.writeString(file, "{not valid json");
        JsonConfigurationRepository repository = new JsonConfigurationRepository(file);
        assertThat(repository.load()).isEqualTo(ApplicationConfiguration.defaults());
        try (var files = Files.list(directory)) { assertThat(files.anyMatch(path -> path.getFileName().toString().startsWith("config.corrupt-"))).isTrue(); }
    }
}
