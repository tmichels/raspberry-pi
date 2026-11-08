package nl.pi.services.music;

import lombok.RequiredArgsConstructor;
import nl.pi.config.MusicProperties;
import nl.pi.services.music.MusicLibrary.Album;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class MusicAlbumAssembler {

    private final MusicProperties properties;
    private final MusicTagProcessor tagProcessor;
    private final MusicTrackMapper trackMapper;

    Album assemble(String name, List<TaggedAudioFile> files) {
        var sharedKeys = tagProcessor.sharedTagKeys(files);
        var tags = tagProcessor.extraTags(files.getFirst().tags(), sharedKeys::contains);
        var tracks = files.stream().map(file -> trackMapper.map(file, sharedKeys)).toList();
        return new Album(name, albumPath(files), tags, tracks);
    }

    private String albumPath(List<TaggedAudioFile> files) {
        var directory = files.getFirst().path().getParent();
        for (var file : files) {
            var trackDirectory = file.path().getParent();
            while (!trackDirectory.startsWith(directory)) {
                directory = directory.getParent();
            }
        }
        return properties.directory().relativize(directory).toString();
    }
}
