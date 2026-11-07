package nl.pi.services;

import java.util.List;
import java.util.Map;

public record MusicLibrary(List<Artist> artists) {

    public record Artist(String name, List<Album> albums) {}

    public record Album(String name, List<Track> tracks) {}

    public record Track(
            String title,
            String path,
            TrackStatus status,
            List<String> missingTags,
            Map<String, String> tags) {}

    public enum TrackStatus {
        OK, MISSING_TAGS
    }
}
