package nl.pi.services.music;

import java.util.List;

public record MusicPlaylist(String filename, List<Entry> entries) {
    public record Entry(String metadata, String trackPath) {
    }
}
