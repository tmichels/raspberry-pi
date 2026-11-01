package nl.pi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("pi.commands")
public record CommandProperties(Path directory) {

    public CommandProperties {
        directory = directory.toAbsolutePath().normalize();
    }
}
