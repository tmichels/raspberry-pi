package nl.pi.repositories;

import nl.pi.services.music.AudioTags;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AudioTagReaderTest {

    @TempDir
    Path directory;

    private final AudioTagReader reader = new AudioTagReader();

    @Test
    void returnsEveryPresentTagWithAllValues() throws Exception {
        var path = createWav("tagged.wav");
        var audioFile = AudioFileIO.read(path.toFile());
        var tag = audioFile.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.ARTIST, "Artist");
        tag.setField(FieldKey.GENRE, "Rock");
        tag.setField(FieldKey.COMMENT, "First comment");
        tag.addField(FieldKey.COMMENT, "Second comment");
        AudioFileIO.write(audioFile);

        var tags = reader.readTags(path);

        assertEquals(Optional.of("Artist"), tags.artist());
        assertEquals(List.of("Rock"), tags.extraTagValues("GENRE"));
        assertEquals(List.of("First comment", "Second comment"), tags.extraTagValues("COMMENT"));
        assertFalse(tags.extraTagKeys().contains("LYRICS"));
        assertThrows(UnsupportedOperationException.class, () -> tags.extraTagKeys().add("OTHER"));
        assertThrows(UnsupportedOperationException.class, () -> tags.extraTagValues("COMMENT").add("Later"));
    }

    @Test
    void skipsBlankValues() throws Exception {
        var path = createWav("blank.wav");
        var audioFile = AudioFileIO.read(path.toFile());
        var tag = audioFile.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.ARTIST, "  ");
        tag.setField(FieldKey.TITLE, " Title ");
        AudioFileIO.write(audioFile);

        assertEquals(new AudioTags(Map.of("TITLE", List.of("Title"))), reader.readTags(path));
    }

    @Test
    void returnsNoTagsWhenTheFileHasNoTagObject() throws Exception {
        var path = Files.createFile(directory.resolve("track.mp3"));
        try (var audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(path.toFile())).thenReturn(mock(AudioFile.class));

            assertEquals(AudioTags.empty(), reader.readTags(path));
        }
    }

    @Test
    void skipsUnsupportedKeysAndKeepsReadingOtherValuesInOrder() {
        var path = directory.resolve("track.mp3");
        var audioFile = mock(AudioFile.class);
        var tag = mock(Tag.class);
        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getAll(FieldKey.ARTIST)).thenReturn(List.of(" First artist ", "", "Second artist"));
        when(tag.getAll(FieldKey.ALBUM)).thenReturn(List.of(" First album ", "Second album"));
        when(tag.getAll(FieldKey.TITLE)).thenReturn(List.of(" First title ", "Second title"));
        when(tag.getAll(FieldKey.GENRE)).thenThrow(new KeyNotFoundException("unsupported"));
        when(tag.getAll(FieldKey.COMMENT)).thenReturn(List.of(" First ", " ", " Second ", "First"));
        when(tag.getAll(FieldKey.MUSICBRAINZ_RELEASEID)).thenReturn(List.of(" dynamic value "));
        try (var audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(path.toFile())).thenReturn(audioFile);

            var tags = reader.readTags(path);

            assertEquals(Optional.of("First artist"), tags.artist());
            assertEquals(Optional.of("First album"), tags.album());
            assertEquals(Optional.of("First title"), tags.title());
            assertEquals(Set.of("COMMENT", "MUSICBRAINZ_RELEASEID"), tags.extraTagKeys());
            assertEquals(List.of("First", "Second", "First"), tags.extraTagValues("COMMENT"));
            assertEquals(List.of("dynamic value"), tags.extraTagValues("MUSICBRAINZ_RELEASEID"));
        }
    }

    @Test
    void suppliesImmutableMapAndListsBeforeConstructingAudioTags() {
        var tag = mock(Tag.class);
        var source = new ArrayList<>(List.of(" Artist ", "Second"));
        when(tag.getAll(FieldKey.ARTIST)).thenReturn(source);
        when(tag.getAll(FieldKey.COMMENT)).thenReturn(List.of(" Comment "));

        var valuesByName = reader.readValuesByName(tag);
        source.clear();

        assertEquals(Map.of("ARTIST", List.of("Artist", "Second"),
                "COMMENT", List.of("Comment")), valuesByName);
        assertThrows(UnsupportedOperationException.class, () -> valuesByName.put("OTHER", List.of("x")));
        assertThrows(UnsupportedOperationException.class, () -> valuesByName.remove("ARTIST"));
        valuesByName.values().forEach(values -> {
            assertThrows(UnsupportedOperationException.class, () -> values.add("x"));
            assertThrows(UnsupportedOperationException.class, () -> values.set(0, "x"));
        });
    }

    @Test
    void suppliesImmutableEmptyMapsForMissingOrEmptyTags() {
        for (var valuesByName : List.of(reader.readValuesByName(null), reader.readValuesByName(mock(Tag.class)))) {
            assertEquals(Map.of(), valuesByName);
            assertThrows(UnsupportedOperationException.class, () -> valuesByName.put("OTHER", List.of("x")));
        }
    }

    @Test
    void throwsAudioReadExceptionForUnreadableFiles() throws Exception {
        var path = Files.writeString(directory.resolve("broken.mp3"), "not an MP3 file");

        assertThrows(AudioReadException.class, () -> reader.readTags(path));
    }

    private Path createWav(String name) throws Exception {
        var path = directory.resolve(name);
        var format = new AudioFormat(8000, 16, 1, true, false);
        try (var audio = new AudioInputStream(new ByteArrayInputStream(new byte[16000]), format, 8000)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, path.toFile());
        }
        return path;
    }
}
