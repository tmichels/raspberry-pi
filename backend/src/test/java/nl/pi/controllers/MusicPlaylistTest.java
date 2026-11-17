package nl.pi.controllers;

import nl.pi.config.MusicProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class MusicPlaylistTest {

    @TempDir
    Path root;

    @Autowired
    MockMvc mvc;

    @Value("${local.server.port}")
    int port;

    @MockitoBean
    MusicProperties properties;

    @MockitoBean
    JwtDecoder jwtDecoder;

    Path album;
    private static final String ALBUM = "Artiést/Album +%&#?";
    private static final String TRACK = "é +%2F&#?.mp3";

    @BeforeEach
    void setUp() throws Exception {
        when(properties.directory()).thenReturn(root);
        album = Files.createDirectories(root.resolve(ALBUM));
        Files.writeString(album.resolve(TRACK), "0123456789");
        Files.writeString(album.resolve("a.mp3"), "other");
        Files.writeString(album.resolve("Muziek é.m3u"),
                "\uFEFF#EXTM3U\r\n#EXTINF:10,Artiést - Titel\r\n" + TRACK + "\r\na.mp3\r\n" + TRACK);
    }

    @Test
    void servesUtf8InlinePlaylistWithOriginalOrderAndTokenFreeEncodedAbsoluteUrls() throws Exception {
        var response = mvc.perform(get("/api/music/playlist").param("path", ALBUM)
                        .header("Host", "music.example:8080"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/x-mpegurl;charset=UTF-8"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn().getResponse();
        var disposition = ContentDisposition.parse(response.getHeader("Content-Disposition"));
        assertEquals("inline", disposition.getType());
        assertEquals("Muziek é.m3u", disposition.getFilename());
        var body = response.getContentAsString(StandardCharsets.UTF_8);
        var urls = body.lines().filter(line -> !line.startsWith("#")).toList();
        assertTrue(body.startsWith("#EXTM3U\n#EXTINF:10,Artiést - Titel\n"));
        assertEquals(3, urls.size());
        assertEquals(urls.getFirst(), urls.getLast());
        assertTrue(urls.getFirst().contains("%C3%A9%20%2B%252F%26%23%3F.mp3"));
        assertTrue(urls.get(1).endsWith("a.mp3"));
        assertFalse(body.contains("token"));
        assertFalse(body.contains("Bearer"));
        for (var url : urls) {
            assertTrue(url.startsWith("http://music.example:8080/api/music/stream?path="));
            mvc.perform(get(URI.create(url))).andExpect(status().isOk());
        }
    }

    @Test
    void usesForwardedHttpsHostPortAndContextForPlayableFullAndRangeUrls() throws Exception {
        var response = mvc.perform(get("/player/api/music/playlist").contextPath("/player")
                        .param("path", ALBUM)
                        .header("Host", "internal:8080")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "music.example")
                        .header("X-Forwarded-Port", "9443"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var url = response.getContentAsString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.startsWith("https:")).findFirst().orElseThrow();
        assertTrue(url.startsWith("https://music.example:9443/player/api/music/stream?path="));
        mvc.perform(get(URI.create(url)).contextPath("/player"))
                .andExpect(status().isOk())
                .andExpect(content().string("0123456789"));
        mvc.perform(get(URI.create(url)).contextPath("/player").header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(content().string("2345"));
        mvc.perform(head(URI.create(url)).contextPath("/player"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Length", "10"));
    }

    @Test
    void returnsNotFoundForAbsentAlbumOrPlaylist() throws Exception {
        mvc.perform(get("/api/music/playlist").param("path", "absent"))
                .andExpect(status().isNotFound());
        Files.delete(album.resolve("Muziek é.m3u"));
        mvc.perform(get("/api/music/playlist").param("path", ALBUM))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "/etc", "../outside", "invalid\0", "a/../../outside"})
    void rejectsInvalidAlbumPathsWithoutRevealingFilesystemPaths(String path) throws Exception {
        mvc.perform(get("/api/music/playlist").param("path", path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Het albumpad is ongeldig."));
    }

    @Test
    void servesAPlaylistFromTheMusicRootForAnExplicitlyEmptyAlbumPath() throws Exception {
        Files.writeString(root.resolve("root.mp3"), "root-track");
        Files.writeString(root.resolve("root.m3u"), "root.mp3");
        var response = mvc.perform(get("/api/music/playlist").param("path", ""))
                .andExpect(status().isOk()).andReturn().getResponse();
        var url = response.getContentAsString(StandardCharsets.UTF_8).lines()
                .filter(line -> !line.startsWith("#")).findFirst().orElseThrow();
        mvc.perform(get(URI.create(url))).andExpect(status().isOk())
                .andExpect(content().string("root-track"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "#EXTM3U", "missing.mp3", "../../../outside.mp3",
            "https://example.org/song.mp3", "file:///etc/passwd"})
    void reportsInvalidPlaylistEntriesAsProblemJson(String text) throws Exception {
        Files.writeString(album.resolve("Muziek é.m3u"), text);
        assertInvalidPlaylist();
    }

    @Test
    void reportsMultiplePlaylistsAndSymlinksAsProblemJson() throws Exception {
        Files.writeString(album.resolve("other.M3U"), "a.mp3");
        assertInvalidPlaylist();
        Files.delete(album.resolve("other.M3U"));
        Files.delete(album.resolve("a.mp3"));
        Files.createSymbolicLink(album.resolve("a.mp3"), album.resolve(TRACK));
        assertInvalidPlaylist();
    }

    @Test
    void reportsIoFailuresUsingTheExistingSafeProblemResponse() throws Exception {
        when(properties.directory()).thenReturn(root.resolve("missing-root"));
        mvc.perform(get("/api/music/playlist").param("path", ALBUM))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "De muziekmap kon niet worden gelezen. Controleer de logs op de Raspberry Pi."));
    }

    @Test
    void requiresTheAlbumPathAndSupportsAnonymousHead() throws Exception {
        mvc.perform(get("/api/music/playlist")).andExpect(status().isBadRequest());
        mvc.perform(head("/api/music/playlist").param("path", ALBUM))
                .andExpect(status().isOk());
    }

    @Test
    void followsGeneratedUrlsOverRealHttpAndSupportsBodylessHead() throws Exception {
        var endpoint = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("http://localhost:" + port).path("/api/music/playlist")
                .queryParam("path", "{album}").encode().buildAndExpand(ALBUM).toUri();
        try (var client = HttpClient.newHttpClient()) {
            var playlist = client.send(HttpRequest.newBuilder(endpoint)
                    .header("X-Forwarded-Proto", "http")
                    .header("X-Forwarded-Host", "localhost")
                    .header("X-Forwarded-Port", Integer.toString(port)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, playlist.statusCode());
            var url = URI.create(playlist.body().lines().filter(line -> !line.startsWith("#"))
                    .findFirst().orElseThrow());
            assertEquals(port, url.getPort());
            var full = client.send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, full.statusCode());
            assertEquals("0123456789", full.body());
            var range = client.send(HttpRequest.newBuilder(url).header("Range", "bytes=2-5").build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(206, range.statusCode());
            assertEquals("2345", range.body());
            for (var uri : new URI[]{endpoint, url}) {
                var head = client.send(HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, head.statusCode());
                assertEquals("", head.body());
            }
        }
    }

    private void assertInvalidPlaylist() throws Exception {
        var response = mvc.perform(get("/api/music/playlist").param("path", ALBUM))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "De afspeellijst is ongeldig. Verwacht één M3U-bestand met geldige lokale audiotracks."))
                .andReturn().getResponse();
        assertFalse(response.getContentAsString().contains(root.toString()));
    }
}
