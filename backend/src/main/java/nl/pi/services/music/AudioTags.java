package nl.pi.services.music;

import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable audiometadata, onafhankelijk van het gebruikte leesformaat.
 * De oorspronkelijke waardelijsten blijven behouden voor vergelijking.
 */
@EqualsAndHashCode
public final class AudioTags {

    public enum RequiredTag {
        ARTIST, ALBUM, TITLE
    }

    private final Map<String, List<String>> valuesByName;
    private final Set<String> extraKeys;

    /**
     * Bewaart de aangeleverde metadata rechtstreeks, zonder defensieve kopie.
     *
     * @param valuesByName immutable map met uitsluitend immutable waardelijsten;
     *                     callers moeten dit vóór constructie garanderen
     */
    public AudioTags(Map<String, List<String>> valuesByName) {
        this.valuesByName = valuesByName;
        var keys = new HashSet<>(this.valuesByName.keySet());
        for (var tag : RequiredTag.values()) {
            keys.remove(tag.name());
        }
        this.extraKeys = Set.copyOf(keys);
    }

    public static AudioTags empty() {
        return new AudioTags(Map.of());
    }

    public Optional<String> artist() {
        return firstValue(RequiredTag.ARTIST);
    }

    public Optional<String> album() {
        return firstValue(RequiredTag.ALBUM);
    }

    public Optional<String> title() {
        return firstValue(RequiredTag.TITLE);
    }

    public boolean contains(RequiredTag tag) {
        return valuesByName.containsKey(tag.name());
    }

    public Set<String> extraTagKeys() {
        return extraKeys;
    }

    public List<String> extraTagValues(String key) {
        return extraKeys.contains(key) ? valuesByName.get(key) : List.of();
    }

    private Optional<String> firstValue(RequiredTag tag) {
        return valuesByName.getOrDefault(tag.name(), List.of()).stream().findFirst();
    }
}
