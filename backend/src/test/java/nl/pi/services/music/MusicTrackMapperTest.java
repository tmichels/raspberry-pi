package nl.pi.services.music;

import nl.pi.config.MusicProperties;
import nl.pi.services.music.MusicLibrary.TrackStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicTrackMapperTest {

    private final MusicTrackMapper mapper =
            new MusicTrackMapper(new MusicProperties(Path.of("/music")), new MusicTagProcessor());

    @Test
    void createsFinalTrackWithFallbackRelativePathAndOnlySpecificTags() {
        var file = new TaggedAudioFile(Path.of("/music/disc/track.wav"), new AudioTags(Map.of(
                "ARTIST", List.of("Artist"), "ALBUM", List.of("Album"),
                "GENRE", List.of("Rock"), "COMMENT", List.of("One", "Two"))));

        var track = mapper.map(file, Set.of("GENRE"));

        assertEquals("track.wav", track.title());
        assertEquals("disc/track.wav", track.path());
        assertEquals(TrackStatus.MISSING_TAGS, track.status());
        assertEquals(List.of("TITLE"), track.missingTags());
        assertEquals(Map.of("COMMENT", "One; Two"), track.tags());
    }

    @Test
    void usesFirstTitleAndReportsCompleteTags() {
        var file = new TaggedAudioFile(Path.of("/music/track.wav"), new AudioTags(Map.of(
                "ARTIST", List.of("Artist"), "ALBUM", List.of("Album"),
                "TITLE", List.of("First", "Second"))));

        var track = mapper.map(file, Set.of());

        assertEquals("First", track.title());
        assertEquals(TrackStatus.OK, track.status());
        assertEquals(List.of(), track.missingTags());
        assertEquals(Map.of(), track.tags());
    }
}
