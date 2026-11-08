package nl.pi.services;

import nl.pi.config.MusicProperties;
import nl.pi.repositories.AudioReadException;
import nl.pi.repositories.AudioTagReader;
import nl.pi.repositories.MusicFileRepository;
import nl.pi.services.MusicLibrary.Album;
import nl.pi.services.MusicLibrary.Artist;
import nl.pi.services.MusicLibrary.Track;
import nl.pi.services.MusicLibrary.TrackStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MusicServiceTest {

    private static final Path DIRECTORY = Path.of("/music");

    @Mock
    private MusicFileRepository fileRepository;

    @Mock
    private AudioTagReader tagReader;

    private MusicService service;

    @BeforeEach
    void setUp() {
        service = new MusicService(new MusicProperties(DIRECTORY), fileRepository, tagReader);
    }

    @Test
    void groupsByTagsRatherThanDirectoryNamesAndSortsArtistsAndAlbums() throws Exception {
        givenFiles("wrong/02.wav", "elsewhere/01.wav", "third.wav", "fourth.wav");
        givenTags("wrong/02.wav", tags("Artist A", "Album A", "Second"));
        givenTags("elsewhere/01.wav", tags("Artist A", "Album A", "First"));
        givenTags("third.wav", tags("Artist A", "Album B", "Third"));
        givenTags("fourth.wav", tags("Artist B", "Album A", "Fourth"));

        var artists = service.getMusic().artists();

        assertEquals(List.of("Artist A", "Artist B"), artists.stream().map(Artist::name).toList());
        var albums = artists.getFirst().albums();
        assertEquals(List.of("Album A", "Album B"), albums.stream().map(Album::name).toList());
        assertEquals(List.of("Second", "First"), albums.getFirst().tracks().stream().map(Track::title).toList());
        var track = albums.getFirst().tracks().getLast();
        assertEquals("elsewhere/01.wav", track.path());
        assertEquals(TrackStatus.OK, track.status());
        assertEquals(List.of(), track.missingTags());
        assertEquals("Fourth", artists.getLast().albums().getFirst().tracks().getFirst().title());
    }

    @Test
    void reportsOnlyTheMissingTagsAndKeepsKnownMetadata() throws Exception {
        givenFiles("partial.wav");
        givenTags("partial.wav", Map.of("ARTIST", List.of("Artist"), "TITLE", List.of("Title")));

        var artist = service.getMusic().artists().getFirst();
        var album = artist.albums().getFirst();
        var track = album.tracks().getFirst();

        assertEquals("Artist", artist.name());
        assertEquals("Onbekend album", album.name());
        assertEquals("Title", track.title());
        assertEquals(TrackStatus.MISSING_TAGS, track.status());
        assertEquals(List.of("ALBUM"), track.missingTags());
    }

    @Test
    void usesDefaultsAndTheFileNameWhenEveryTagIsMissing() throws Exception {
        givenFiles("sub/untagged.wav");
        givenTags("sub/untagged.wav", Map.of());

        var artist = service.getMusic().artists().getFirst();
        var album = artist.albums().getFirst();
        var track = album.tracks().getFirst();

        assertEquals("Onbekende artiest", artist.name());
        assertEquals("Onbekend album", album.name());
        assertEquals("untagged.wav", track.title());
        assertEquals("sub/untagged.wav", track.path());
        assertEquals(TrackStatus.MISSING_TAGS, track.status());
        assertEquals(List.of("ARTIST", "ALBUM", "TITLE"), track.missingTags());
    }

    @Test
    void groupsOnTheFirstValueAndJoinsMultipleValuesWithSemicolons() throws Exception {
        givenFiles("multi.wav");
        givenTags("multi.wav", Map.of(
                "ARTIST", List.of("First", "Second"),
                "ALBUM", List.of("Album"),
                "TITLE", List.of("Title"),
                "COMMENT", List.of("One", "Two")));

        var artist = service.getMusic().artists().getFirst();
        var album = artist.albums().getFirst();
        var track = album.tracks().getFirst();

        assertEquals("First", artist.name());
        assertEquals(Map.of("COMMENT", "One; Two"), album.tags());
        assertEquals(Map.of(), track.tags());
    }

    @Test
    void movesAdditionalTagsToTheAlbumForASingleTrack() throws Exception {
        givenFiles("single.wav");
        givenTags("single.wav", tagsWithExtras("Artist", "Album", "Title", Map.of(
                "COMMENT", List.of("A comment"),
                "CUSTOM_TAG", List.of("A custom value"))));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(Map.of("COMMENT", "A comment", "CUSTOM_TAG", "A custom value"), album.tags());
        assertEquals(Map.of(), album.tracks().getFirst().tags());
    }

    @Test
    void movesIdenticalAdditionalTagValuesToTheAlbumIncludingUnknownTags() throws Exception {
        givenFiles("1.wav", "2.wav");
        var extras = Map.of(
                "GENRE", List.of("Rock"),
                "TRACK_TOTAL", List.of("2"),
                "COMMENT", List.of("One", "Two"),
                "CUSTOM_TAG", List.of("Shared"));
        givenTags("1.wav", tagsWithExtras("Artist", "Album", "First", extras));
        givenTags("2.wav", tagsWithExtras("Artist", "Album", "Second", extras));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(Map.of("GENRE", "Rock", "TRACK_TOTAL", "2", "COMMENT", "One; Two", "CUSTOM_TAG", "Shared"), album.tags());
        album.tracks().forEach(track -> assertEquals(Map.of(), track.tags()));
    }

    @Test
    void keepsDifferentAdditionalTagValuesOnTheirTracks() throws Exception {
        givenFiles("1.wav", "2.wav");
        givenTags("1.wav", tagsWithExtras("Artist", "Album", "First", Map.of(
                "GENRE", List.of("Rock"), "COMMENT", List.of("First comment"))));
        givenTags("2.wav", tagsWithExtras("Artist", "Album", "Second", Map.of(
                "GENRE", List.of("Metal"), "COMMENT", List.of("Second comment"))));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(Map.of(), album.tags());
        assertEquals(Map.of("GENRE", "Rock", "COMMENT", "First comment"), album.tracks().getFirst().tags());
        assertEquals(Map.of("GENRE", "Metal", "COMMENT", "Second comment"), album.tracks().getLast().tags());
    }

    @Test
    void keepsTagsOnTracksWhenTheyAreMissingFromTheFirstTrack() throws Exception {
        givenFiles("1.wav", "2.wav");
        givenTags("1.wav", tags("Artist", "Album", "First"));
        givenTags("2.wav", tagsWithExtras("Artist", "Album", "Second", Map.of("COMMENT", List.of("Only here"))));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(Map.of(), album.tags());
        assertEquals(Map.of(), album.tracks().getFirst().tags());
        assertEquals(Map.of("COMMENT", "Only here"), album.tracks().getLast().tags());
    }

    @Test
    void keepsTagsOnTracksWhenTheyAreMissingFromALaterTrack() throws Exception {
        givenFiles("1.wav", "2.wav", "3.wav");
        var extras = Map.of("GENRE", List.of("Rock"));
        givenTags("1.wav", tagsWithExtras("Artist", "Album", "First", extras));
        givenTags("2.wav", tagsWithExtras("Artist", "Album", "Second", extras));
        givenTags("3.wav", tags("Artist", "Album", "Third"));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(Map.of(), album.tags());
        assertEquals(Map.of("GENRE", "Rock"), album.tracks().getFirst().tags());
        assertEquals(Map.of("GENRE", "Rock"), album.tracks().get(1).tags());
        assertEquals(Map.of(), album.tracks().getLast().tags());
    }

    @Test
    void keepsTagIntersectionsSeparateAcrossAlbumsAndArtists() throws Exception {
        givenFiles("a1.wav", "a2.wav", "b1.wav");
        givenTags("a1.wav", tagsWithExtras("Artist A", "Album", "First", Map.of("GENRE", List.of("Rock"))));
        givenTags("a2.wav", tagsWithExtras("Artist A", "Other Album", "Second", Map.of("GENRE", List.of("Jazz"))));
        givenTags("b1.wav", tagsWithExtras("Artist B", "Album", "Third", Map.of("GENRE", List.of("Jazz"))));

        var artists = service.getMusic().artists();

        assertEquals(Map.of("GENRE", "Rock"), artists.getFirst().albums().getFirst().tags());
        assertEquals(Map.of("GENRE", "Jazz"), artists.getFirst().albums().getLast().tags());
        assertEquals(Map.of("GENRE", "Jazz"), artists.getLast().albums().getFirst().tags());
    }

    @Test
    void neverDuplicatesEqualOrDifferentTitlesInTags() throws Exception {
        givenFiles("1.wav", "2.wav", "3.wav");
        givenTags("1.wav", tagsWithExtras("Artist", "Album", "Same", Map.of("TITLE", List.of("Same"))));
        givenTags("2.wav", tagsWithExtras("Artist", "Album", "Same", Map.of("TITLE", List.of("Same"))));
        givenTags("3.wav", tagsWithExtras("Artist", "Album", "Different", Map.of("TITLE", List.of("Different"))));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(List.of("Same", "Same", "Different"), album.tracks().stream().map(Track::title).toList());
        assertEquals(Map.of(), album.tags());
        album.tracks().forEach(track -> assertEquals(Map.of(), track.tags()));
    }

    @Test
    void keepsTitlesDedicatedWhenAllTracksHaveTheSameTitle() throws Exception {
        givenFiles("1.wav", "2.wav");
        givenTags("1.wav", tags("Artist", "Album", "Same"));
        givenTags("2.wav", tags("Artist", "Album", "Same"));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(List.of("Same", "Same"), album.tracks().stream().map(Track::title).toList());
        assertEquals(Map.of(), album.tags());
        album.tracks().forEach(track -> assertEquals(Map.of(), track.tags()));
    }

    @Test
    void comparesMultivalueTagsIncludingTheirOrder() throws Exception {
        givenFiles("1.wav", "2.wav", "3.wav");
        givenTags("1.wav", tagsWithExtras("Artist", "Album", "First", Map.of(
                "COMMENT", List.of("First", "Second"))));
        givenTags("2.wav", tagsWithExtras("Artist", "Album", "Second", Map.of(
                "COMMENT", List.of("First", "Second"))));
        givenTags("3.wav", tagsWithExtras("Artist", "Album", "Third", Map.of(
                "COMMENT", List.of("Second", "First"))));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(Map.of(), album.tags());
        assertEquals(Map.of("COMMENT", "First; Second"), album.tracks().getFirst().tags());
        assertEquals(Map.of("COMMENT", "First; Second"), album.tracks().get(1).tags());
        assertEquals(Map.of("COMMENT", "Second; First"), album.tracks().getLast().tags());
    }

    @Test
    void comparesOriginalMultivalueListsEvenWhenTheirJoinedStringsCollide() throws Exception {
        givenFiles("1.wav", "2.wav");
        givenTags("1.wav", tagsWithExtras("Artist", "Album", "First", Map.of(
                "COMMENT", List.of("A; B"))));
        givenTags("2.wav", tagsWithExtras("Artist", "Album", "Second", Map.of(
                "COMMENT", List.of("A", "B"))));

        var album = service.getMusic().artists().getFirst().albums().getFirst();

        assertEquals(Map.of(), album.tags());
        assertEquals(Map.of("COMMENT", "A; B"), album.tracks().getFirst().tags());
        assertEquals(Map.of("COMMENT", "A; B"), album.tracks().getLast().tags());
    }

    @Test
    void treatsUnreadableTracksAsTracksWithoutTags() throws Exception {
        givenFiles("broken.mp3", "valid.wav");
        when(tagReader.readTags(DIRECTORY.resolve("broken.mp3")))
                .thenThrow(new AudioReadException(DIRECTORY.resolve("broken.mp3"), new IOException()));
        givenTags("valid.wav", tags("Artist", "Album", "Title"));

        var artists = service.getMusic().artists();

        assertEquals(2, artists.size());
        assertEquals(TrackStatus.OK, artists.getFirst().albums().getFirst().tracks().getFirst().status());
        var track = artists.getLast().albums().getFirst().tracks().getFirst();
        assertEquals("Onbekende artiest", artists.getLast().name());
        assertEquals("broken.mp3", track.title());
        assertEquals(TrackStatus.MISSING_TAGS, track.status());
        assertEquals(List.of("ARTIST", "ALBUM", "TITLE"), track.missingTags());
        assertEquals(Map.of(), track.tags());
    }

    @Test
    void returnsAnEmptyLibraryWhenThereAreNoFiles() throws Exception {
        when(fileRepository.findAudioFiles()).thenReturn(List.of());

        assertEquals(new MusicLibrary(List.of()), service.getMusic());
    }

    @Test
    void propagatesDirectoryErrors() throws Exception {
        when(fileRepository.findAudioFiles()).thenThrow(new IOException("Map ontbreekt"));

        assertThrows(IOException.class, service::getMusic);
    }

    private void givenFiles(String... names) throws IOException {
        when(fileRepository.findAudioFiles()).thenReturn(
                java.util.Arrays.stream(names).map(DIRECTORY::resolve).toList());
    }

    private void givenTags(String name, Map<String, List<String>> tags) {
        when(tagReader.readTags(DIRECTORY.resolve(name))).thenReturn(tags);
    }

    private Map<String, List<String>> tagsWithExtras(
            String artist, String album, String title, Map<String, List<String>> extras) {
        var tags = new HashMap<>(tags(artist, album, title));
        tags.putAll(extras);
        return tags;
    }

    private Map<String, List<String>> tags(String artist, String album, String title) {
        return Map.of("ARTIST", List.of(artist), "ALBUM", List.of(album), "TITLE", List.of(title));
    }
}
