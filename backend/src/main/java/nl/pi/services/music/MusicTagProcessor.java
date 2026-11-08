package nl.pi.services.music;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

@Service
class MusicTagProcessor {

    private static final String VALUE_SEPARATOR = "; ";

    List<String> missingTags(AudioTags tags) {
        return Arrays.stream(AudioTags.RequiredTag.values())
                .filter(tag -> !tags.contains(tag))
                .map(Enum::name)
                .toList();
    }

    Set<String> sharedTagKeys(List<TaggedAudioFile> files) {
        var firstTags = files.getFirst().tags();
        var sharedKeys = new HashSet<>(firstTags.extraTagKeys());
        for (var file : files.subList(1, files.size())) {
            sharedKeys.removeIf(key -> !file.tags().extraTagKeys().contains(key)
                    || !file.tags().extraTagValues(key).equals(firstTags.extraTagValues(key)));
        }
        return sharedKeys;
    }

    Map<String, String> extraTags(AudioTags tags, Predicate<String> filter) {
        var selected = new TreeMap<String, String>();
        tags.extraTagKeys().forEach(key -> {
            if (filter.test(key)) {
                selected.put(key, String.join(VALUE_SEPARATOR, tags.extraTagValues(key)));
            }
        });
        return selected;
    }
}
