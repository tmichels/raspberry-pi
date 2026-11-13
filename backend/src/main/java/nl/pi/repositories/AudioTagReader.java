package nl.pi.repositories;

import nl.pi.services.music.AudioTags;
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
     * Leest audiometadata en behoudt alle niet-lege waarden in hun oorspronkelijke volgorde.
     * De map en alle waardelijsten zijn immutable vóór overdracht aan {@link AudioTags}.
     *
     * @throws AudioReadException wanneer tags niet gelezen kunnen worden
     */
    public AudioTags readTags(Path path) {
        try {
            return new AudioTags(readValuesByName(AudioFileIO.read(path.toFile()).getTag()));
        } catch (IOException | CannotReadException | TagException
                 | ReadOnlyFileException | InvalidAudioFrameException exception) {
            throw new AudioReadException(path, exception);
        }
    }

    Map<String, List<String>> readValuesByName(Tag tag) {
        if (tag == null) {
            return Map.of();
        }
        var valuesByName = new TreeMap<String, List<String>>();
        for (var key : FieldKey.values()) {
            var values = readValues(tag, key);
            if (!values.isEmpty()) {
                valuesByName.put(key.name(), values);
            }
        }
        return Map.copyOf(valuesByName);
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
