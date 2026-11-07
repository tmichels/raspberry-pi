package nl.pi.repositories;

import lombok.RequiredArgsConstructor;
import nl.pi.config.MusicProperties;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class MusicFileRepository {

    private static final int MAX_DEPTH = 5;
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "flac", "wav", "ogg", "m4a", "aac");

    private final MusicProperties properties;

    public List<Path> findAudioFiles() throws IOException {
        var directory = properties.directory();
        if (!Files.isDirectory(directory)) {
            throw new NotDirectoryException(directory.toString());
        }
        try (var paths = Files.find(
                directory,
                MAX_DEPTH,
                (path, attributes) -> attributes.isRegularFile() && isAudioFile(path))) {
            return paths.sorted().toList();
        }
    }

    private boolean isAudioFile(Path path) {
        var name = path.getFileName().toString();
        var separator = name.lastIndexOf('.');
        if (separator < 0) {
            return false;
        }
        var extension = name.substring(separator + 1).toLowerCase(Locale.ROOT);
        return AUDIO_EXTENSIONS.contains(extension);
    }
}
