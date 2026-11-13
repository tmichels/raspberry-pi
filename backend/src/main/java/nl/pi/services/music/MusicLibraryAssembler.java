package nl.pi.services.music;

import lombok.RequiredArgsConstructor;
import nl.pi.services.music.MusicLibrary.Album;
import nl.pi.services.music.MusicLibrary.Artist;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class MusicLibraryAssembler {

    private static final String UNKNOWN_ARTIST = "Onbekende artiest";
    private static final String UNKNOWN_ALBUM = "Onbekend album";

    private final MusicAlbumAssembler albumAssembler;

    MusicLibrary assemble(List<TaggedAudioFile> taggedAudioFiles) {
        var filesByAlbum = groupFilesByAlbum(taggedAudioFiles);
        return new MusicLibrary(assembleArtists(filesByAlbum));
    }

    private Map<AlbumKey, List<TaggedAudioFile>> groupFilesByAlbum(List<TaggedAudioFile> taggedAudioFiles) {
        Map<AlbumKey, List<TaggedAudioFile>> filesByAlbum = new HashMap<>();
        for (var taggedAudioFile : taggedAudioFiles) {
            var albumKey = new AlbumKey(
                    taggedAudioFile.tags().artist().orElse(UNKNOWN_ARTIST),
                    taggedAudioFile.tags().album().orElse(UNKNOWN_ALBUM));
            var filesInAlbum = filesByAlbum.computeIfAbsent(albumKey, _ -> new ArrayList<>());
            filesInAlbum.add(taggedAudioFile);
        }
        return filesByAlbum;
    }

    private List<Artist> assembleArtists(Map<AlbumKey, List<TaggedAudioFile>> filesByAlbum) {
        Map<String, List<Album>> albumsByArtist = new HashMap<>();
        for (var entry : filesByAlbum.entrySet()) {
            var key = entry.getKey();
            var album = albumAssembler.assemble(key.albumName(), entry.getValue());
            var albums = albumsByArtist.computeIfAbsent(key.artistName(), ignored -> new ArrayList<>());
            albums.add(album);
        }
        List<Artist> artists = new ArrayList<>();
        for (var entry : albumsByArtist.entrySet()) {
            var albums = entry.getValue();
            albums.sort(Comparator.comparing(Album::name));
            artists.add(new Artist(entry.getKey(), List.copyOf(albums)));
        }
        artists.sort(Comparator.comparing(Artist::name));
        return List.copyOf(artists);
    }

    private record AlbumKey(String artistName, String albumName) {
    }
}
