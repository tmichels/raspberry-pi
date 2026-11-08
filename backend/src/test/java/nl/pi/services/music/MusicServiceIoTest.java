package nl.pi.services.music;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import nl.pi.config.MusicProperties;
import nl.pi.repositories.AudioReadException;
import nl.pi.repositories.AudioTagReader;
import nl.pi.repositories.MusicFileRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MusicServiceIoTest {

    @Test
    void readsInRepositoryOrderAndLogsUnreadableFileBeforePassingFallbackToAssembler() throws IOException {
        var repository = mock(MusicFileRepository.class);
        var reader = mock(AudioTagReader.class);
        var assembler = mock(MusicLibraryAssembler.class);
        var properties = new MusicProperties(Path.of("/music"));
        var first = properties.directory().resolve("broken.mp3");
        var second = properties.directory().resolve("valid.wav");
        var tags = new AudioTags(Map.of("TITLE", List.of("Valid")));
        var expected = new MusicLibrary(List.of());
        when(repository.findAudioFiles()).thenReturn(List.of(first, second));
        when(reader.readTags(first)).thenThrow(new AudioReadException(first, new IOException("broken")));
        when(reader.readTags(second)).thenReturn(tags);
        var files = List.of(new TaggedAudioFile(first, AudioTags.empty()), new TaggedAudioFile(second, tags));
        when(assembler.assemble(files)).thenReturn(expected);
        var service = new MusicService(properties, repository, reader, assembler);
        var logger = (Logger) LoggerFactory.getLogger(MusicService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertSame(expected, service.getMusic());
            var order = inOrder(repository, reader, assembler);
            order.verify(repository).findAudioFiles();
            order.verify(reader).readTags(first);
            order.verify(reader).readTags(second);
            order.verify(assembler).assemble(files);
            assertEquals(1, appender.list.size());
            assertEquals(Level.WARN, appender.list.getFirst().getLevel());
            assertTrue(appender.list.getFirst().getFormattedMessage().contains("broken.mp3"));
            assertEquals(AudioReadException.class.getName(),
                    appender.list.getFirst().getThrowableProxy().getClassName());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void propagatesRepositoryFailureWithoutReadingOrAssembling() throws IOException {
        var repository = mock(MusicFileRepository.class);
        var reader = mock(AudioTagReader.class);
        var assembler = mock(MusicLibraryAssembler.class);
        var failure = new IOException("unavailable");
        when(repository.findAudioFiles()).thenThrow(failure);
        var service = new MusicService(new MusicProperties(Path.of("/music")), repository, reader, assembler);

        assertSame(failure, assertThrows(IOException.class, service::getMusic));
        verifyNoInteractions(reader, assembler);
    }
}
