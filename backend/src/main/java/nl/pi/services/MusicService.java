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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {

    private static final String UNKNOWN_ARTIST = "Onbekende artiest";
    private static final String UNKNOWN_ALBUM = "Onbekend album";
    private static final List<String> REQUIRED_TAGS = List.of("ARTIST", "ALBUM", "TITLE");
    private static final String VALUE_SEPARATOR = "; ";

    private final MusicProperties properties;
    private final MusicFileRepository fileRepository;
    private final AudioTagReader tagReader;

    public MusicLibrary getMusic() throws IOException {
        List<Artist> artists = new ArrayList<>();
        Map<Album, List<Map<String, List<String>>>> tagsByAlbum = new IdentityHashMap<>();
        List<Path> audioFiles = fileRepository.findAudioFiles();
        for (var audioFile : audioFiles) {
            var tags = readTags(audioFile);
            var artist = findOrAddArtist(artists, firstValue(tags, "ARTIST", UNKNOWN_ARTIST));
            var album = findOrAddAlbum(artist, firstValue(tags, "ALBUM", UNKNOWN_ALBUM));
            album.tracks().add(toTrack(audioFile, tags));
            tagsByAlbum.computeIfAbsent(album, ignored -> new ArrayList<>()).add(tags);
        }
        artists.forEach(artist -> artist.albums().replaceAll(album ->
                assignAlbumTags(album, tagsByAlbum.get(album))));
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
                    var album = new Album(name, "", new TreeMap<>(), new ArrayList<>());
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
        return new Track(title, relativePath(path), status, missingTags, selectTags(tags, key -> !REQUIRED_TAGS.contains(key)));
    }

    private Album assignAlbumTags(Album album, List<Map<String, List<String>>> trackTags) {
        var sharedKeys = sharedTagKeys(trackTags);
        var albumTags = selectTags(trackTags.getFirst(), sharedKeys::contains);
        assignTrackTags(album.tracks(), trackTags, sharedKeys);
        return new Album(album.name(), albumPath(album.tracks()), albumTags, album.tracks());
    }

    private Set<String> sharedTagKeys(List<Map<String, List<String>>> trackTags) {
        var firstTrackTags = trackTags.getFirst();
        var sharedKeys = new HashSet<>(firstTrackTags.keySet());
        sharedKeys.removeIf(REQUIRED_TAGS::contains);
        for (var tags : trackTags.subList(1, trackTags.size())) {
            sharedKeys.removeIf(key -> !tags.containsKey(key)
                    || !tags.get(key).equals(firstTrackTags.get(key)));
        }
        return sharedKeys;
    }

    private void assignTrackTags(
            List<Track> tracks, List<Map<String, List<String>>> trackTags, Set<String> sharedKeys) {
        for (int index = 0; index < tracks.size(); index++) {
            var track = tracks.get(index);
            tracks.set(index, new Track(
                    track.title(),
                    track.path(),
                    track.status(),
                    track.missingTags(),
                    selectTags(trackTags.get(index), key -> !REQUIRED_TAGS.contains(key) && !sharedKeys.contains(key))));
        }
    }

    private String albumPath(List<Track> tracks) {
        var directory = properties.directory().resolve(tracks.getFirst().path()).getParent();
        for (var track : tracks) {
            var trackDirectory = properties.directory().resolve(track.path()).getParent();
            while (!trackDirectory.startsWith(directory)) {
                directory = directory.getParent();
            }
        }
        return relativePath(directory);
    }

    private String relativePath(Path path) {
        return properties.directory().relativize(path).toString();
    }

    private String firstValue(Map<String, List<String>> tags, String key, String defaultValue) {
        return tags.getOrDefault(key, List.of()).stream().findFirst().orElse(defaultValue);
    }

    private Map<String, String> selectTags(Map<String, List<String>> tags, Predicate<String> filter) {
        var selected = new TreeMap<String, String>();
        tags.forEach((key, values) -> {
            if (filter.test(key)) {
                selected.put(key, String.join(VALUE_SEPARATOR, values));
            }
        });
        return selected;
    }

}
