package nl.pi.services;

import nl.pi.config.MusicProperties;
import nl.pi.services.MusicLibrary.TrackStatus;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MusicServiceTest {

    @TempDir
    Path directory;

    private MusicService service;

    @BeforeEach
    void setUp() {
        service = new MusicService(new MusicProperties(directory));
    }

    @Test
    void groupsActualAudioTagsRatherThanDirectoryNamesAndSortsResults() throws Exception {
        createTrack("wrong-artist/wrong-album/02.wav", "Artist A", "Album A", "Second");
        createTrack("elsewhere/01.WAV", "Artist A", "Album A", "First");
        createTrack("third.wav", "Artist A", "Album B", "Third");
        createTrack("fourth.wav", "Artist B", "Album A", "Fourth");
        Files.writeString(directory.resolve("cover.jpg"), "not music");
        Files.writeString(directory.resolve("mp3"), "no extension");
        Files.createDirectory(directory.resolve("folder.mp3"));

        var artists = service.getMusic().artists();

        assertEquals(List.of("Artist A", "Artist B"), artists.stream().map(MusicLibrary.Artist::name).toList());
        var albums = artists.getFirst().albums();
        assertEquals(List.of("Album A", "Album B"), albums.stream().map(MusicLibrary.Album::name).toList());
        assertEquals(List.of("First", "Second"),
                albums.getFirst().tracks().stream().map(MusicLibrary.Track::title).toList());
        var track = albums.getFirst().tracks().getFirst();
        assertEquals("elsewhere/01.WAV", track.path());
        assertEquals(TrackStatus.OK, track.status());
        assertEquals(List.of(), track.missingTags());
        assertEquals("Fourth", artists.getLast().albums().getFirst().tracks().getFirst().title());
    }

    @Test
    void reportsOnlyTheMissingTagsAndKeepsKnownMetadata() throws Exception {
        createTrack("partial.wav", "Artist", "", "Title");

        var artist = service.getMusic().artists().getFirst();
        var album = artist.albums().getFirst();
        var track = album.tracks().getFirst();

        assertEquals("Artist", artist.name());
        assertEquals("Onbekend album", album.name());
        assertEquals("Title", track.title());
        assertEquals(TrackStatus.MISSING_TAGS, track.status());
        assertEquals(List.of("ALBUM"), track.missingTags());
    }

    @Test
    void reportsAllMissingTagsInAnUntaggedAudioFile() throws Exception {
        createTrack("untagged.wav", "", "", "");

        var artist = service.getMusic().artists().getFirst();
        var album = artist.albums().getFirst();
        var track = album.tracks().getFirst();

        assertEquals("Onbekende artiest", artist.name());
        assertEquals("Onbekend album", album.name());
        assertEquals("untagged.wav", track.title());
        assertEquals(TrackStatus.MISSING_TAGS, track.status());
        assertEquals(List.of("ARTIST", "ALBUM", "TITLE"), track.missingTags());
    }

    @Test
    void treatsAbsentTagObjectsAndWhitespaceTagsAsMissing() throws Exception {
        var path = Files.createFile(directory.resolve("track.mp3"));
        var audioFile = mock(AudioFile.class);
        try (var reader = mockStatic(AudioFileIO.class)) {
            reader.when(() -> AudioFileIO.read(path.toFile())).thenReturn(audioFile);
            var track = service.getMusic().artists().getFirst().albums().getFirst().tracks().getFirst();
            assertEquals(TrackStatus.MISSING_TAGS, track.status());
            assertEquals(List.of("ARTIST", "ALBUM", "TITLE"), track.missingTags());
        }

        Files.delete(path);
        createTrack("blank.wav", "  ", "Album", "  ");
        var track = service.getMusic().artists().getFirst().albums().getFirst().tracks().getFirst();
        assertEquals(TrackStatus.MISSING_TAGS, track.status());
        assertEquals(List.of("ARTIST", "TITLE"), track.missingTags());
        assertEquals("blank.wav", track.title());
    }

    @Test
    void keepsUnreadableTracksAlongsideReadableTracks() throws Exception {
        Files.writeString(directory.resolve("broken.mp3"), "not an MP3 file");
        createTrack("valid.wav", "Artist", "Album", "Title");

        var artists = service.getMusic().artists();
        assertEquals(2, artists.size());
        assertEquals(TrackStatus.OK, artists.getFirst().albums().getFirst().tracks().getFirst().status());
        var track = artists.getLast().albums().getFirst().tracks().getFirst();
        assertEquals("broken.mp3", track.title());
        assertEquals("broken.mp3", track.path());
        assertEquals(TrackStatus.READ_ERROR, track.status());
        assertEquals(List.of(), track.missingTags());
    }

    @Test
    void returnsAnEmptyLibraryForAnEmptyDirectory() throws IOException {
        assertEquals(new MusicLibrary(List.of()), service.getMusic());
    }

    @Test
    void scansAgainOnEachRequest() throws Exception {
        assertTrue(service.getMusic().artists().isEmpty());
        createTrack("new.wav", "Artist", "Album", "New");
        assertEquals(1, service.getMusic().artists().size());
    }

    @Test
    void doesNotFollowSymbolicFilesOrDirectories() throws Exception {
        var target = createTrack("original/track.wav", "Artist", "Album", "Title");
        Files.createSymbolicLink(directory.resolve("alias.wav"), target);
        Files.createSymbolicLink(directory.resolve("alias-directory"), target.getParent());

        var tracks = service.getMusic().artists().getFirst().albums().getFirst().tracks();

        assertEquals(1, tracks.size());
        assertEquals("original/track.wav", tracks.getFirst().path());
    }

    @Test
    void reportsMissingDirectoriesAndPathsThatAreNotDirectories() throws IOException {
        var missingService = new MusicService(new MusicProperties(directory.resolve("missing")));
        assertThrows(IOException.class, missingService::getMusic);

        var file = Files.createFile(directory.resolve("file"));
        var fileService = new MusicService(new MusicProperties(file));
        assertThrows(IOException.class, fileService::getMusic);
    }

    private Path createTrack(String name, String artist, String album, String title) throws Exception {
        var path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        var format = new AudioFormat(8000, 16, 1, true, false);
        try (var audio = new AudioInputStream(new ByteArrayInputStream(new byte[16000]), format, 8000)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, path.toFile());
        }
        if (!artist.isEmpty() || !album.isEmpty() || !title.isEmpty()) {
            var audioFile = AudioFileIO.read(path.toFile());
            var tag = audioFile.getTagOrCreateAndSetDefault();
            tag.setField(FieldKey.ARTIST, artist);
            tag.setField(FieldKey.ALBUM, album);
            tag.setField(FieldKey.TITLE, title);
            AudioFileIO.write(audioFile);
        }
        return path;
    }
}
