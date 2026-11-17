package nl.pi.services.music;

import lombok.RequiredArgsConstructor;
import nl.pi.repositories.InvalidPlaylistException;
import nl.pi.repositories.MusicFileRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MusicPlaylistService {

    private final MusicFileRepository fileRepository;

    public Optional<MusicPlaylist> getPlaylist(String albumPath) throws IOException {
        var source = fileRepository.findPlaylist(albumPath);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        var file = source.get();
        var entries = new ArrayList<MusicPlaylist.Entry>();
        String metadata = null;
        var content = file.content().startsWith("\uFEFF") ? file.content().substring(1) : file.content();
        for (var raw : content.lines().toList()) {
            var line = raw.strip();
            if (line.isEmpty() || line.equals("#EXTM3U")) {
                continue;
            }
            if (line.startsWith("#EXTINF:")) {
                if (metadata != null || !line.matches("#EXTINF:(-1|\\d+(?:\\.\\d+)?),[^\\p{Cntrl}]*")) {
                    throw new InvalidPlaylistException();
                }
                metadata = line;
            } else if (line.startsWith("#")) {
                // Alleen standaard EXTINF beïnvloedt de uitvoer; geen HLS/VLC-directieven.
                if (line.startsWith("#EXT")) {
                    throw new InvalidPlaylistException();
                }
            } else {
                entries.add(new MusicPlaylist.Entry(metadata,
                        fileRepository.resolvePlaylistTrack(file.albumPath(), line)));
                metadata = null;
            }
        }
        if (entries.isEmpty() || metadata != null) {
            throw new InvalidPlaylistException();
        }
        return Optional.of(new MusicPlaylist(file.filename(), List.copyOf(entries)));
    }
}
