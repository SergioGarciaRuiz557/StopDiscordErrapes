package com.discordaudioguard.config;

/**
 * Persistence port for the application's complete preferences.
 *
 * <p>The application layer depends on this interface rather than the concrete JSON
 * format, allowing storage to be replaced or an in-memory implementation to be used
 * in tests.</p>
 */
public interface ConfigurationRepository {
    /**
     * Retrieves persisted configuration or defaults when no usable configuration exists.
     *
     * @return complete configuration, never {@code null}
     */
    ApplicationConfiguration load();

    /**
     * Persists a complete configuration snapshot.
     *
     * @param configuration configuration to persist
     */
    void save(ApplicationConfiguration configuration);
}
