package nl.pi.repositories;

import nl.pi.config.MusicProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MusicFileRepositoryTest {

    @TempDir
    Path directory;

    private MusicFileRepository repository() {
        return new MusicFileRepository(new MusicProperties(directory));
    }

    @Test
    void findsAudioFilesCaseInsensitivelyAndSortsThem() throws IOException {
        var second = Files.createFile(directory.resolve("b.mp3"));
        var nested = Files.createDirectories(directory.resolve("sub"));
        var first = Files.createFile(nested.resolve("a.FLAC"));
        Files.createFile(directory.resolve("cover.jpg"));
        Files.createFile(directory.resolve("mp3"));
        Files.createDirectory(directory.resolve("folder.mp3"));

        assertEquals(List.of(second, first), repository().findAudioFiles());
    }

    @Test
    void ignoresFilesDeeperThanTheMaximumDepth() throws IOException {
        var deep = Files.createDirectories(directory.resolve("1/2/3/4"));
        var reachable = Files.createFile(deep.resolve("ok.mp3"));
        Files.createFile(Files.createDirectories(deep.resolve("5")).resolve("too-deep.mp3"));

        assertEquals(List.of(reachable), repository().findAudioFiles());
    }

    @Test
    void returnsNothingForAnEmptyDirectory() throws IOException {
        assertEquals(List.of(), repository().findAudioFiles());
    }

    @Test
    void doesNotFollowSymbolicFilesOrDirectories() throws IOException {
        var original = Files.createDirectories(directory.resolve("original"));
        var target = Files.createFile(original.resolve("track.wav"));
        Files.createSymbolicLink(directory.resolve("alias.wav"), target);
        Files.createSymbolicLink(directory.resolve("alias-directory"), original);

        assertEquals(List.of(target), repository().findAudioFiles());
    }

    @Test
    void reportsMissingDirectoriesAndPathsThatAreNotDirectories() throws IOException {
        var missing = new MusicFileRepository(new MusicProperties(directory.resolve("missing")));
        assertThrows(IOException.class, missing::findAudioFiles);

        var file = Files.createFile(directory.resolve("file"));
        var notADirectory = new MusicFileRepository(new MusicProperties(file));
        assertThrows(IOException.class, notADirectory::findAudioFiles);
    }
}
