package nl.pi.controllers;

import nl.pi.config.MusicProperties;
import nl.pi.repositories.AudioTagReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MusicStreamTest {

    private static final String TRACK_PATH = "Artist/Album name/track.mp3";
    private static final String AUDIO = "0123456789";

    @TempDir
    Path directory;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MusicProperties properties;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    AudioTagReader tagReader;

    @BeforeEach
    void configureMusicRoot() throws Exception {
        when(properties.directory()).thenReturn(directory);
        var album = Files.createDirectories(directory.resolve("Artist/Album name"));
        Files.writeString(album.resolve("track.mp3"), AUDIO);
        clearInvocations(properties);
    }

    @Test
    void streamsTheCompleteTrackThroughTheRealServiceAndRepository() throws Exception {
        mvc.perform(get("/api/music/stream").param("path", TRACK_PATH).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Content-Length", "10"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().doesNotExist("Content-Range"))
                .andExpect(content().string(AUDIO));
        verifyNoInteractions(tagReader);
    }

    @ParameterizedTest
    @CsvSource({"MP3,audio/mpeg", "FLAC,audio/flac", "WAV,audio/wav", "OGG,audio/ogg",
            "M4A,audio/mp4", "AAC,audio/aac"})
    void usesTheAudioMimeTypeForEveryLibraryExtension(String extension, String mediaType) throws Exception {
        var name = "track." + extension;
        Files.writeString(directory.resolve(name), AUDIO);

        mvc.perform(get("/api/music/stream").param("path", name).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(mediaType))
                .andExpect(content().string(AUDIO));
    }

    @ParameterizedTest
    @CsvSource({"bytes=2-5,bytes 2-5/10,2345", "bytes=7-,bytes 7-9/10,789",
            "bytes=-3,bytes 7-9/10,789", "bytes=8-99,bytes 8-9/10,89"})
    void letsSpringHandleClosedOpenSuffixAndClampedRanges(String range, String contentRange, String body)
            throws Exception {
        mvc.perform(get("/api/music/stream").param("path", TRACK_PATH).header("Range", range).with(jwt()))
                .andExpect(status().isPartialContent())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Range", contentRange))
                .andExpect(header().string("Content-Length", Integer.toString(body.length())))
                .andExpect(content().string(body));
    }

    @Test
    void letsSpringHandleMultipleRanges() throws Exception {
        mvc.perform(get("/api/music/stream").param("path", TRACK_PATH)
                        .header("Range", "bytes=0-1,8-9").with(jwt()))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Type", startsWith("multipart/byteranges;")))
                .andExpect(content().string(containsString("Content-Type: audio/mpeg")))
                .andExpect(content().string(containsString("Content-Range: bytes 0-1/10")))
                .andExpect(content().string(containsString("Content-Range: bytes 8-9/10")))
                .andExpect(content().string(containsString("\r\n\r\n01\r\n")))
                .andExpect(content().string(containsString("\r\n\r\n89\r\n")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes=10-", "bytes=5-2", "bytes=invalid", "items=0-1",
            "bytes=-0", "bytes=0-1,10-", "bytes=0-5,4-9"})
    void returnsRangeNotSatisfiableForInvalidRanges(String range) throws Exception {
        mvc.perform(get("/api/music/stream").param("path", TRACK_PATH).header("Range", range).with(jwt()))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */10"))
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void streamsAnEmptyFileButRejectsItsByteRange() throws Exception {
        Files.createFile(directory.resolve("empty.mp3"));
        mvc.perform(get("/api/music/stream").param("path", "empty.mp3").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Length", "0"))
                .andExpect(content().string(""));
        mvc.perform(get("/api/music/stream").param("path", "empty.mp3").header("Range", "bytes=0-").with(jwt()))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */0"))
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void requiresAuthenticationForFullAndPartialStreams() throws Exception {
        mvc.perform(get("/api/music/stream").param("path", TRACK_PATH))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/music/stream").param("path", TRACK_PATH).header("Range", "bytes=0-1"))
                .andExpect(status().isUnauthorized());
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));
        mvc.perform(get("/api/music/stream").param("path", TRACK_PATH).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(properties, tagReader);
    }

    @Test
    void requiresTheTrackPathParameter() throws Exception {
        mvc.perform(get("/api/music/stream").with(jwt())).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/outside.mp3", "../outside.mp3", "a/../../outside.mp3", "invalid\0.mp3"})
    void rejectsUnsafeTrackPathsWithProblemDetails(String path) throws Exception {
        mvc.perform(get("/api/music/stream").param("path", path).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Het trackpad is ongeldig."));
    }

    @Test
    void rejectsAnOutsideSymlink(@TempDir Path outside) throws Exception {
        var track = Files.writeString(outside.resolve("track.mp3"), "outside");
        Files.createSymbolicLink(directory.resolve("alias.mp3"), track);

        mvc.perform(get("/api/music/stream").param("path", "alias.mp3").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Het trackpad is ongeldig."));
    }

    @Test
    void returnsNotFoundForMissingAndNonAudioFiles() throws Exception {
        Files.writeString(directory.resolve("notes.txt"), "not audio");
        Files.createDirectory(directory.resolve("folder.mp3"));

        for (var path : new String[]{"missing.mp3", "notes.txt", "folder.mp3"}) {
            mvc.perform(get("/api/music/stream").param("path", path).with(jwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(""));
        }
    }

    @Test
    void reportsAnUnavailableMusicRootWithoutExposingItsPath() throws Exception {
        when(properties.directory()).thenReturn(directory.resolve("missing"));

        mvc.perform(get("/api/music/stream").param("path", TRACK_PATH).with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "De muziekmap kon niet worden gelezen. Controleer de logs op de Raspberry Pi."));
    }
}
