package nl.pi.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.pi.config.MusicProperties;
import nl.pi.repositories.AudioReadException;
import nl.pi.repositories.AudioTagReader;
import nl.pi.repositories.MusicFileRepository;
import nl.pi.services.MusicLibrary.Album;
import nl.pi.services.MusicLibrary.Artist;
import nl.pi.services.MusicLibrary.Track;
import nl.pi.services.MusicLibrary.TrackStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {

    private static final String UNKNOWN_ARTIST = "Onbekende artiest";
    private static final String UNKNOWN_ALBUM = "Onbekend album";
    private static final List<String> REQUIRED_TAGS = List.of("ARTIST", "ALBUM", "TITLE");

    private final MusicProperties properties;
    private final MusicFileRepository fileRepository;
    private final AudioTagReader tagReader;

    public MusicLibrary getMusic() throws IOException {
        List<Artist> artists = new ArrayList<>();
        List<Path> audioFiles = fileRepository.findAudioFiles();
        for (var audioFile : audioFiles) {
            var tags = readTags(audioFile);
            var artist = findOrAddArtist(artists, firstValue(tags, "ARTIST", UNKNOWN_ARTIST));
            var album = findOrAddAlbum(artist, firstValue(tags, "ALBUM", UNKNOWN_ALBUM));
            album.tracks().add(toTrack(audioFile, tags));
        }
        artists.sort(Comparator.comparing(Artist::name));
        artists.forEach(artist -> artist.albums().sort(Comparator.comparing(Album::name)));
        return new MusicLibrary(artists);
    }

    private Artist findOrAddArtist(List<Artist> artists, String name) {
        return artists.stream()
                .filter(artist -> artist.name().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    var artist = new Artist(name, new ArrayList<>());
                    artists.add(artist);
                    return artist;
                });
    }

    private Album findOrAddAlbum(Artist artist, String name) {
        return artist.albums().stream()
                .filter(album -> album.name().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    var album = new Album(name, new ArrayList<>());
                    artist.albums().add(album);
                    return album;
                });
    }

    private Map<String, List<String>> readTags(Path path) {
        try {
            return tagReader.readTags(path);
        } catch (AudioReadException exception) {
            log.warn("Tags konden niet gelezen worden: {}", relativePath(path), exception);
            return Map.of();
        }
    }

    private Track toTrack(Path path, Map<String, List<String>> tags) {
        var missingTags = REQUIRED_TAGS.stream().filter(key -> !tags.containsKey(key)).toList();
        var status = missingTags.isEmpty() ? TrackStatus.OK : TrackStatus.MISSING_TAGS;
        var title = firstValue(tags, "TITLE", path.getFileName().toString());
        return new Track(title, relativePath(path), status, missingTags, joinValues(tags));
    }

    private String relativePath(Path path) {
        return properties.directory().relativize(path).toString();
    }

    private String firstValue(Map<String, List<String>> tags, String key, String defaultValue) {
        return tags.getOrDefault(key, List.of()).stream().findFirst().orElse(defaultValue);
    }

    private Map<String, String> joinValues(Map<String, List<String>> tags) {
        var joined = new TreeMap<String, String>();
        tags.forEach((key, values) -> joined.put(key, String.join("; ", values)));
        return Map.copyOf(joined);
    }
}
