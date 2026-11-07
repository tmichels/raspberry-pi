package nl.pi.services;

import java.util.List;

public record MusicLibrary(List<Artist> artists) {

    public record Artist(String name, List<Album> albums) {}

    public record Album(String name, List<Track> tracks) {}

    public record Track(String title, String path, TrackStatus status, List<String> missingTags) {}

    public enum TrackStatus {
        OK, MISSING_TAGS, READ_ERROR
    }
}
