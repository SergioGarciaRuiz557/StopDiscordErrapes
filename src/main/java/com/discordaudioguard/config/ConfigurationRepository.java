package com.discordaudioguard.config;

public interface ConfigurationRepository {
    ApplicationConfiguration load();
    void save(ApplicationConfiguration configuration);
}
