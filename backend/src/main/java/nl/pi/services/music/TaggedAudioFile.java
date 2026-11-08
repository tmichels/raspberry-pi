package nl.pi.services.music;

import java.nio.file.Path;

record TaggedAudioFile(Path path, AudioTags tags) {
}
