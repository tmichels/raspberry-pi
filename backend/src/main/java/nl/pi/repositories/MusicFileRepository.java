package nl.pi.repositories;

import lombok.RequiredArgsConstructor;
import nl.pi.config.MusicProperties;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    public Optional<byte[]> findAlbumArt(String albumPath) throws IOException {
        Path relativePath;
        try {
            relativePath = Path.of(albumPath);
        } catch (InvalidPathException exception) {
            throw new InvalidAlbumPathException();
        }
        if (relativePath.isAbsolute() || relativePath.normalize().startsWith("..")) {
            throw new InvalidAlbumPathException();
        }

        var root = properties.directory().toRealPath();
        var image = root.resolve(relativePath).normalize().resolve("albumart_backup/cover.jpg");
        try {
            var realImage = image.toRealPath();
            if (!realImage.equals(image)) {
                throw new InvalidAlbumPathException();
            }
            return Optional.of(Files.readAllBytes(realImage));
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        }
    }

    public Optional<Path> findAudioFile(String trackPath) throws IOException {
        Path relativePath;
        try {
            relativePath = Path.of(trackPath);
        } catch (InvalidPathException exception) {
            throw new InvalidTrackPathException();
        }
        if (trackPath.isBlank() || relativePath.isAbsolute() || relativePath.normalize().startsWith("..")) {
            throw new InvalidTrackPathException();
        }

        var root = properties.directory().toRealPath();
        var file = root.resolve(relativePath).normalize();
        if (!Files.isDirectory(root)) {
            throw new NotDirectoryException(root.toString());
        }
        var relativeFile = root.relativize(file);
        try {
            var current = root;
            for (var component : relativeFile) {
                current = current.resolve(component);
                var attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink()) {
                    throw new InvalidTrackPathException();
                }
                if (!current.equals(file) && !attributes.isDirectory()) {
                    return Optional.empty();
                }
            }
            var realFile = file.toRealPath();
            if (!realFile.startsWith(root) || !realFile.equals(file)) {
                throw new InvalidTrackPathException();
            }
            if (relativeFile.getNameCount() > MAX_DEPTH || !Files.isRegularFile(realFile) || !isAudioFile(realFile)) {
                return Optional.empty();
            }
            return Optional.of(realFile);
        } catch (NoSuchFileException | NotDirectoryException exception) {
            return Optional.empty();
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
