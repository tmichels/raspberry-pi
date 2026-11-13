package nl.pi.services.music;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AudioTagsTest {

    @Test
    void exposesFirstSemanticValuesAndOnlyAdditionalKeys() {
        var tags = new AudioTags(Map.of(
                "ARTIST", List.of("First artist", "Second artist"),
                "ALBUM", List.of("First album", "Second album"),
                "TITLE", List.of("First title", "Second title"),
                "CUSTOM_TAG", List.of("One", "Two", "One")));

        assertEquals(Optional.of("First artist"), tags.artist());
        assertEquals(Optional.of("First album"), tags.album());
        assertEquals(Optional.of("First title"), tags.title());
        assertEquals(Set.of("CUSTOM_TAG"), tags.extraTagKeys());
        assertEquals(List.of("One", "Two", "One"), tags.extraTagValues("CUSTOM_TAG"));
        for (var tag : AudioTags.RequiredTag.values()) {
            assertTrue(tags.contains(tag));
            assertEquals(List.of(), tags.extraTagValues(tag.name()));
        }
        assertEquals(List.of(), tags.extraTagValues("ABSENT"));
    }

    @Test
    void distinguishesAbsentKeysFromPresentEmptyListsWithoutInventingValues() {
        var empty = AudioTags.empty();
        var present = new AudioTags(Map.of("ARTIST", List.of(), "CUSTOM_TAG", List.of()));

        assertEquals(Optional.empty(), empty.artist());
        assertEquals(Optional.empty(), empty.album());
        assertEquals(Optional.empty(), empty.title());
        assertFalse(empty.contains(AudioTags.RequiredTag.ARTIST));
        assertEquals(Set.of(), empty.extraTagKeys());
        assertEquals(Optional.empty(), present.artist());
        assertTrue(present.contains(AudioTags.RequiredTag.ARTIST));
        assertEquals(Set.of("CUSTOM_TAG"), present.extraTagKeys());
    }

    @Test
    void exposesSuppliedImmutableValuesWithoutCopying() {
        var artist = List.of("Original artist");
        var comment = List.of("Original", "Second");
        var source = Map.of("ARTIST", artist, "COMMENT", comment);
        var tags = new AudioTags(source);
        var file = new TaggedAudioFile(Path.of("/music/file.wav"), tags);

        assertSame(tags, file.tags());
        assertEquals(Optional.of("Original artist"), tags.artist());
        assertSame(comment, tags.extraTagValues("COMMENT"));
        assertThrows(UnsupportedOperationException.class, () -> tags.extraTagKeys().add("OTHER"));
        assertThrows(UnsupportedOperationException.class, () -> tags.extraTagValues("COMMENT").add("x"));
        assertThrows(UnsupportedOperationException.class, () -> tags.extraTagValues("COMMENT").set(0, "x"));
        assertThrows(UnsupportedOperationException.class, () -> tags.extraTagValues("ABSENT").add("x"));
        assertThrows(UnsupportedOperationException.class, () -> tags.extraTagValues("ARTIST").add("x"));
        assertThrows(UnsupportedOperationException.class, () -> AudioTags.empty().extraTagKeys().add("OTHER"));
    }

    @Test
    void comparesOriginalListsRatherThanTheirJoinedRepresentation() {
        var single = new AudioTags(Map.of("COMMENT", List.of("A; B")));
        var multiple = new AudioTags(Map.of("COMMENT", List.of("A", "B")));
        var reversed = new AudioTags(Map.of("COMMENT", List.of("B", "A")));

        assertEquals(multiple, new AudioTags(Map.of("COMMENT", List.of("A", "B"))));
        assertEquals(multiple.hashCode(), new AudioTags(Map.of("COMMENT", List.of("A", "B"))).hashCode());
        assertNotEquals(single, multiple);
        assertNotEquals(multiple, reversed);
    }
}
