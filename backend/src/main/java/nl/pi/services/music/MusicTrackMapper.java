package nl.pi.services.music;

import lombok.RequiredArgsConstructor;
import nl.pi.config.MusicProperties;
import nl.pi.services.music.MusicLibrary.Track;
import nl.pi.services.music.MusicLibrary.TrackStatus;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
class MusicTrackMapper {

    private final MusicProperties properties;
    private final MusicTagProcessor tagProcessor;

    Track map(TaggedAudioFile file, Set<String> sharedKeys) {
        var missingTags = tagProcessor.missingTags(file.tags());
        var status = missingTags.isEmpty() ? TrackStatus.OK : TrackStatus.MISSING_TAGS;
        var title = file.tags().title().orElse(file.path().getFileName().toString());
        var tags = tagProcessor.extraTags(file.tags(), key -> !sharedKeys.contains(key));
        return new Track(title, properties.directory().relativize(file.path()).toString(), status, missingTags, tags);
    }
}
