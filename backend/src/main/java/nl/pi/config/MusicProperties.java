package nl.pi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("pi.music")
public record MusicProperties(Path directory) {

    public MusicProperties {
        directory = directory.toAbsolutePath().normalize();
    }
}
