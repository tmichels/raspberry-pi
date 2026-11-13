package nl.pi.services.music;

import nl.pi.config.MusicProperties;
import nl.pi.services.music.MusicLibrary.Track;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicAlbumAssemblerTest {

    private final MusicProperties properties = new MusicProperties(Path.of("/music"));
    private final MusicTagProcessor processor = new MusicTagProcessor();
    private final MusicAlbumAssembler assembler =
            new MusicAlbumAssembler(properties, processor, new MusicTrackMapper(properties, processor));

    @Test
    void combinesDiscDirectoriesAndBuildsTracksInInputOrder() {
        var album = assembler.assemble("Album", List.of(
                file("Artist/Album/CD2/02.wav", "Second", "Rock", "2"),
                file("Artist/Album/CD1/01.wav", "First", "Rock", "1")));

        assertEquals("Artist/Album", album.path());
        assertEquals(Map.of("GENRE", "Rock"), album.tags());
        assertEquals(List.of("Second", "First"), album.tracks().stream().map(Track::title).toList());
        assertEquals(Map.of("NUMBER", "2"), album.tracks().getFirst().tags());
        assertEquals(Map.of("NUMBER", "1"), album.tracks().getLast().tags());
    }

    @Test
    void returnsRootForTracksInDifferentDirectoriesAndDirectoryForSingleTrack() {
        var first = file("one/track.wav", "First", "Rock", "1");
        var second = file("two/track.wav", "Second", "Jazz", "2");

        assertEquals("", assembler.assemble("Album", List.of(first, second)).path());
        var single = assembler.assemble("Album", List.of(first));
        assertEquals("one", single.path());
        assertEquals(Map.of("GENRE", "Rock", "NUMBER", "1"), single.tags());
        assertEquals(Map.of(), single.tracks().getFirst().tags());
    }

    private TaggedAudioFile file(String path, String title, String genre, String number) {
        return new TaggedAudioFile(properties.directory().resolve(path), new AudioTags(Map.of(
                "ARTIST", List.of("Artist"), "ALBUM", List.of("Album"), "TITLE", List.of(title),
                "GENRE", List.of(genre), "NUMBER", List.of(number))));
    }
}
