plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
}

group = "com.discordaudioguard"
version = "0.1.1"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.8")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

application {
    mainClass = "com.discordaudioguard.application.DiscordAudioGuardApplication"
    mainModule = "com.discordaudioguard"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

jlink {
    imageZip = layout.buildDirectory.file("distributions/discord-audio-guard-${project.version}-windows.zip")
    options = listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages", "--bind-services")
    launcher {
        name = "DiscordAudioGuard"
    }
    jpackage {
        installerName = "DiscordAudioGuard"
        imageName = "DiscordAudioGuard"
        appVersion = project.version.toString()
        installerType = "exe"
        imageOptions = listOf("--icon", file("src/main/resources/icons/DiscordAudioGuard.ico").absolutePath)
        installerOptions = listOf("--win-menu", "--win-shortcut", "--vendor", "Discord Audio Guard")
    }
}
