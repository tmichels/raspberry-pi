package nl.pi.services.music;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.pi.config.MusicProperties;
import nl.pi.repositories.AudioReadException;
import nl.pi.repositories.AudioTagReader;
import nl.pi.repositories.MusicFileRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {

    private final MusicProperties properties;
    private final MusicFileRepository fileRepository;
    private final AudioTagReader tagReader;
    private final MusicLibraryAssembler libraryAssembler;

    public MusicLibrary getMusic() throws IOException {
        var taggedAudioFiles = fileRepository.findAudioFiles().stream()
                .map(path -> new TaggedAudioFile(path, readTags(path)))
                .toList();
        return libraryAssembler.assemble(taggedAudioFiles);
    }

    private AudioTags readTags(Path path) {
        try {
            return tagReader.readTags(path);
        } catch (AudioReadException exception) {
            log.warn("Tags konden niet gelezen worden: {}", properties.directory().relativize(path), exception);
            return AudioTags.empty();
        }
    }
}
