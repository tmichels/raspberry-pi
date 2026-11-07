package nl.pi.repositories;

import java.nio.file.Path;

public class AudioReadException extends RuntimeException {

    public AudioReadException(Path path, Throwable cause) {
        super("Audiobestand kon niet worden gelezen: " + path, cause);
    }
}
