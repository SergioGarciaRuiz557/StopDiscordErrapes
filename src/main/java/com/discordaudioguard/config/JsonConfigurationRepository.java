package com.discordaudioguard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Repository that serializes configuration as human-readable JSON.
 *
 * <p>By default it uses {@code ~/.discord-audio-guard/config.json}. Writes first go to
 * a sibling temporary file, which then replaces the destination, preferably through
 * an atomic move. An interruption during serialization therefore cannot leave the
 * primary file partially written.</p>
 *
 * <p>A missing JSON file yields defaults. If a file exists but is corrupt, it is
 * renamed with a timestamp for diagnosis and defaults are used; I/O failures are
 * logged without bringing down the UI.</p>
 */
public final class JsonConfigurationRepository implements ConfigurationRepository {
    /** Log for read, write, and recovery failures. */
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonConfigurationRepository.class);
    /** Exact path of the JSON file managed by this instance. */
    private final Path path;
    /** Serializer configured to produce an easily inspectable file. */
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Creates the repository in the user's private configuration directory. */
    public JsonConfigurationRepository() {
        this(Path.of(System.getProperty("user.home"), ".discord-audio-guard", "config.json"));
    }

    /**
     * Creates a repository at an explicit path, primarily for tests.
     *
     * @param path JSON file to read and replace
     */
    public JsonConfigurationRepository(Path path) { this.path = path; }

    /**
     * {@inheritDoc}
     *
     * <p>Returns any valid configuration first; on corruption, preserves the original
     * and returns {@link ApplicationConfiguration#defaults()}.</p>
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>Creates required directories and uses atomic replacement when supported by
     * the file system, with a conventional move as fallback.</p>
     */
    @Override
    public void save(ApplicationConfiguration configuration) {
        try {
            Files.createDirectories(path.getParent());
            // Keep the temporary file in the same directory to maximize atomic-move support.
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

    /** Moves unreadable JSON to a unique name and frees the primary path. */
    private void preserveCorruptFile() {
        try {
            String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.move(path, path.resolveSibling("config.corrupt-" + suffix + ".json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.warn("No se pudo conservar una copia de la configuración dañada", exception);
        }
    }
}
