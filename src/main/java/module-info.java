module com.discordaudioguard {
    requires java.desktop;
    requires javafx.controls;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;

    exports com.discordaudioguard.application;
    exports com.discordaudioguard.audio.api;
    exports com.discordaudioguard.audio.device;
    exports com.discordaudioguard.audio.engine;
    exports com.discordaudioguard.audio.format;
    exports com.discordaudioguard.audio.processing;
    exports com.discordaudioguard.config;
    exports com.discordaudioguard.tools;
    exports com.discordaudioguard.util;

    opens com.discordaudioguard.application to javafx.graphics;
    opens com.discordaudioguard.config to com.fasterxml.jackson.databind;
    opens com.discordaudioguard.audio.format to com.fasterxml.jackson.databind;
    opens com.discordaudioguard.audio.processing to com.fasterxml.jackson.databind;
}
