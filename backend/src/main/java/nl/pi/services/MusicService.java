package nl.pi.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.pi.config.MusicProperties;
import nl.pi.services.MusicLibrary.Album;
import nl.pi.services.MusicLibrary.Artist;
import nl.pi.services.MusicLibrary.Track;
import nl.pi.services.MusicLibrary.TrackStatus;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.SupportedFileFormat;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {

    private static final String UNKNOWN_ARTIST = "Onbekende artiest";
    private static final String UNKNOWN_ALBUM = "Onbekend album";
    private static final Set<String> AUDIO_EXTENSIONS = Arrays.stream(SupportedFileFormat.values())
            .map(SupportedFileFormat::getFilesuffix)
            .collect(Collectors.toUnmodifiableSet());

    private final MusicProperties properties;

    public MusicLibrary getMusic() throws IOException {
        Map<String, Map<String, List<Track>>> artists = new TreeMap<>();
        for (var path : findTracks()) {
            var taggedTrack = readTrack(path);
            artists.computeIfAbsent(taggedTrack.artist(), _ -> new TreeMap<>())
                    .computeIfAbsent(taggedTrack.album(), _ -> new ArrayList<>())
                    .add(taggedTrack.track());
        }
        return new MusicLibrary(artists.entrySet().stream()
                .map(artist -> new Artist(artist.getKey(), artist.getValue().entrySet().stream()
                        .map(album -> new Album(album.getKey(), List.copyOf(album.getValue())))
                        .toList()))
                .toList());
    }

    private List<Path> findTracks() throws IOException {
        var directory = properties.directory();
        if (!Files.readAttributes(directory, BasicFileAttributes.class).isDirectory()) {
            throw new NotDirectoryException(directory.toString());
        }
        try (var paths = Files.find(directory, Integer.MAX_VALUE,
                (path, attributes) -> attributes.isRegularFile() && isAudioFile(path))) {
            return paths.sorted().toList();
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
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

    private TaggedTrack readTrack(Path path) {
        var relativePath = properties.directory().relativize(path).toString();
        try {
            var tag = AudioFileIO.read(path.toFile()).getTag();
            var missingTags = new ArrayList<String>();
            var artist = readTag(tag, FieldKey.ARTIST, UNKNOWN_ARTIST, missingTags);
            var album = readTag(tag, FieldKey.ALBUM, UNKNOWN_ALBUM, missingTags);
            var title = readTag(tag, FieldKey.TITLE, path.getFileName().toString(), missingTags);
            var status = missingTags.isEmpty() ? TrackStatus.OK : TrackStatus.MISSING_TAGS;
            return new TaggedTrack(artist, album,
                    new Track(title, relativePath, status, List.copyOf(missingTags)));
        } catch (IOException | CannotReadException | TagException
                 | ReadOnlyFileException | InvalidAudioFrameException exception) {
            log.warn("Muziekbestand kon niet worden gelezen: {}", relativePath, exception);
            return new TaggedTrack(UNKNOWN_ARTIST, UNKNOWN_ALBUM,
                    new Track(path.getFileName().toString(), relativePath, TrackStatus.READ_ERROR, List.of()));
        }
    }

    private String readTag(Tag tag, FieldKey key, String fallback, List<String> missingTags) {
        var value = tag == null ? "" : tag.getFirst(key).strip();
        if (value.isEmpty()) {
            missingTags.add(key.name());
            return fallback;
        }
        return value;
    }

    private record TaggedTrack(String artist, String album, Track track) {}
}
