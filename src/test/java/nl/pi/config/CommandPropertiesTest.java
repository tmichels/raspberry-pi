package nl.pi.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPropertiesTest {

    @Test
    void convertsRelativeDirectoryToNormalizedAbsolutePath() {
        var properties = new CommandProperties(Path.of("data", "..", "files"));

        assertTrue(properties.directory().isAbsolute());
        assertEquals(Path.of("").toAbsolutePath().resolve("files"), properties.directory());
    }

    @Test
    void preservesNormalizedAbsoluteDirectory() {
        Path directory = Path.of("").toAbsolutePath().resolve("data").normalize();

        assertEquals(directory, new CommandProperties(directory).directory());
    }
}
