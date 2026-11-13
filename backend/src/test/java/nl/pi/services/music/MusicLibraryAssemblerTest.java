package nl.pi.services.music;

import nl.pi.config.MusicProperties;
import nl.pi.services.music.MusicLibrary.Album;
import nl.pi.services.music.MusicLibrary.Artist;
import nl.pi.services.music.MusicLibrary.Track;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicLibraryAssemblerTest {

    private final MusicProperties properties = new MusicProperties(Path.of("/music"));
    private final MusicTagProcessor processor = new MusicTagProcessor();
    private final MusicLibraryAssembler assembler = new MusicLibraryAssembler(
            new MusicAlbumAssembler(properties, processor, new MusicTrackMapper(properties, processor)));

    @Test
    void groupsByFirstTagValuesAndSortsWithoutChangingTrackOrder() {
        var files = List.of(
                file("wrong/2.wav", "B", "Z", "Second"),
                file("elsewhere/1.wav", "A", "Z", "First"),
                file("third.wav", "A", "A", "Third"),
                file("other/4.wav", "B", "Z", "Fourth"));

        var artists = assembler.assemble(files).artists();

        assertEquals(List.of("A", "B"), artists.stream().map(Artist::name).toList());
        assertEquals(List.of("A", "Z"), artists.getFirst().albums().stream().map(Album::name).toList());
        assertEquals(List.of("First"), artists.getFirst().albums().getLast().tracks()
                .stream().map(Track::title).toList());
        assertEquals(List.of("Z"), artists.getLast().albums().stream().map(Album::name).toList());
        assertEquals(List.of("Second", "Fourth"), artists.getLast().albums().getFirst().tracks()
                .stream().map(Track::title).toList());
        assertEquals("", artists.getLast().albums().getFirst().path());
    }

    @Test
    void sortsArtistsAndEveryArtistsAlbumsInStringNaturalOrder() {
        var files = List.of(
                file("1.wav", "a", "a", "First"),
                file("2.wav", "Z", "a", "Second"),
                file("3.wav", "a", "Z", "Third"),
                file("4.wav", "Z", "Z", "Fourth"));

        var artists = assembler.assemble(files).artists();

        assertEquals(List.of("Z", "a"), artists.stream().map(Artist::name).toList());
        for (var artist : artists) {
            assertEquals(List.of("Z", "a"), artist.albums().stream().map(Album::name).toList());
        }
    }

    @Test
    void usesFallbacksForMissingAndEmptyValuesAndHandlesEmptyLibrary() {
        var missing = new TaggedAudioFile(properties.directory().resolve("missing.wav"), AudioTags.empty());
        var empty = new TaggedAudioFile(properties.directory().resolve("empty.wav"),
                new AudioTags(Map.of("ARTIST", List.of(), "ALBUM", List.of())));

        var artists = assembler.assemble(List.of(missing, empty)).artists();

        assertEquals("Onbekende artiest", artists.getFirst().name());
        assertEquals("Onbekend album", artists.getFirst().albums().getFirst().name());
        assertEquals(2, artists.getFirst().albums().getFirst().tracks().size());
        assertEquals(List.of(), assembler.assemble(List.of()).artists());
    }

    private TaggedAudioFile file(String path, String artist, String album, String title) {
        return new TaggedAudioFile(properties.directory().resolve(path), new AudioTags(Map.of(
                "ARTIST", List.of(artist, "Ignored"), "ALBUM", List.of(album, "Ignored"),
                "TITLE", List.of(title))));
    }
}
