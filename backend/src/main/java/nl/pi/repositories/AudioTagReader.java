package nl.pi.repositories;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Repository
public class AudioTagReader {

    /**
     * Geeft per tagnaam (naam van {@link FieldKey}) alle niet-lege waarden terug.
     *
     * @throws AudioReadException wanneer het bestand niet gelezen kan worden
     */
    public Map<String, List<String>> readTags(Path path) {
        try {
            return toMap(AudioFileIO.read(path.toFile()).getTag());
        } catch (IOException | CannotReadException | TagException
                 | ReadOnlyFileException | InvalidAudioFrameException exception) {
            throw new AudioReadException(path, exception);
        }
    }

    private Map<String, List<String>> toMap(Tag tag) {
        if (tag == null) {
            return Map.of();
        }
        var tags = new TreeMap<String, List<String>>();
        for (var key : FieldKey.values()) {
            var values = readValues(tag, key);
            if (!values.isEmpty()) {
                tags.put(key.name(), values);
            }
        }
        return Map.copyOf(tags);
    }

    private List<String> readValues(Tag tag, FieldKey key) {
        try {
            return tag.getAll(key).stream()
                    .map(String::strip)
                    .filter(value -> !value.isEmpty())
                    .toList();
        } catch (KeyNotFoundException ignored) {
            // Dit audioformaat ondersteunt deze generieke tagsleutel niet.
            return List.of();
        }
    }
}
