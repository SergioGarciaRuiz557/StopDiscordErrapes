package com.discordaudioguard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class JsonConfigurationRepository implements ConfigurationRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonConfigurationRepository.class);
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonConfigurationRepository() {
        this(Path.of(System.getProperty("user.home"), ".discord-audio-guard", "config.json"));
    }

    public JsonConfigurationRepository(Path path) { this.path = path; }

    @Override
    public ApplicationConfiguration load() {
        if (!Files.exists(path)) return ApplicationConfiguration.defaults();
        try {
            return mapper.readValue(path.toFile(), ApplicationConfiguration.class);
        } catch (Exception exception) {
            LOGGER.error("La configuración está dañada; se usarán valores predeterminados: {}", path, exception);
            preserveCorruptFile();
            return ApplicationConfiguration.defaults();
        }
    }

    @Override
    public void save(ApplicationConfiguration configuration) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            mapper.writeValue(temporary.toFile(), configuration);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("No se pudo guardar la configuración en {}", path, exception);
        }
    }

    private void preserveCorruptFile() {
        try {
            String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.move(path, path.resolveSibling("config.corrupt-" + suffix + ".json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.warn("No se pudo conservar una copia de la configuración dañada", exception);
        }
    }
}
