package nl.pi.services.music;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicTagProcessorTest {

    private final MusicTagProcessor processor = new MusicTagProcessor();

    @Test
    void comparesOriginalValuesInOrderAndExcludesRequiredTags() {
        var first = file(Map.of(
                "ARTIST", List.of("Artist"), "ALBUM", List.of("Album"), "TITLE", List.of("Title"),
                "COLLISION", List.of("A; B"), "ORDER", List.of("A", "B"),
                "COMMON", List.of("One", "Two"), "FIRST_ONLY", List.of("First")));
        var second = file(Map.of(
                "ARTIST", List.of("Artist"), "ALBUM", List.of("Album"), "TITLE", List.of("Title"),
                "COLLISION", List.of("A", "B"), "ORDER", List.of("B", "A"),
                "COMMON", List.of("One", "Two"), "SECOND_ONLY", List.of("Second")));

        var shared = processor.sharedTagKeys(List.of(first, second));

        assertEquals(Set.of("COMMON"), shared);
        assertEquals(Map.of("COMMON", "One; Two"), processor.extraTags(first.tags(), shared::contains));
        assertEquals(Map.of("COLLISION", "A; B", "ORDER", "A; B", "FIRST_ONLY", "First"),
                processor.extraTags(first.tags(), key -> !shared.contains(key)));
    }

    @Test
    void detectsMissingKeysRatherThanEmptyValueLists() {
        var tags = new AudioTags(Map.of("ARTIST", List.of("Artist"), "TITLE", List.<String>of()));

        assertEquals(List.of("ALBUM"), processor.missingTags(tags));
        assertEquals("fallback", tags.title().orElse("fallback"));
        assertEquals("fallback", tags.album().orElse("fallback"));
    }

    @Test
    void distinguishesSharedEmptyExtraValuesFromAnAbsentExtraTag() {
        var first = file(Map.of("CUSTOM_TAG", List.of()));
        var second = file(Map.of("CUSTOM_TAG", List.of()));
        var missing = file(Map.of());

        assertEquals(Set.of("CUSTOM_TAG"), processor.sharedTagKeys(List.of(first, second)));
        assertEquals(Map.of("CUSTOM_TAG", ""), processor.extraTags(first.tags(), key -> true));
        assertEquals(Set.of(), processor.sharedTagKeys(List.of(first, missing)));
        assertEquals(Set.of(), processor.sharedTagKeys(List.of(missing, first)));
        assertEquals(List.of("ARTIST", "ALBUM", "TITLE"), processor.missingTags(first.tags()));
    }

    private TaggedAudioFile file(Map<String, List<String>> tags) {
        return new TaggedAudioFile(Path.of("/music/file.wav"), new AudioTags(tags));
    }
}
