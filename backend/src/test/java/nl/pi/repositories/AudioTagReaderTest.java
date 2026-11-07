package nl.pi.repositories;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

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

        assertEquals(List.of("Artist"), tags.get("ARTIST"));
        assertEquals(List.of("Rock"), tags.get("GENRE"));
        assertEquals(List.of("First comment", "Second comment"), tags.get("COMMENT"));
        assertFalse(tags.containsKey("LYRICS"));
    }

    @Test
    void skipsBlankValues() throws Exception {
        var path = createWav("blank.wav");
        var audioFile = AudioFileIO.read(path.toFile());
        var tag = audioFile.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.ARTIST, "  ");
        tag.setField(FieldKey.TITLE, " Title ");
        AudioFileIO.write(audioFile);

        assertEquals(Map.of("TITLE", List.of("Title")), reader.readTags(path));
    }

    @Test
    void returnsNoTagsWhenTheFileHasNoTagObject() throws Exception {
        var path = Files.createFile(directory.resolve("track.mp3"));
        try (var audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(path.toFile())).thenReturn(mock(AudioFile.class));

            assertEquals(Map.of(), reader.readTags(path));
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
