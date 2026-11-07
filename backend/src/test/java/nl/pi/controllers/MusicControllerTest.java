package nl.pi.controllers;

import nl.pi.services.CommandService;
import nl.pi.services.MusicLibrary;
import nl.pi.services.MusicLibrary.Album;
import nl.pi.services.MusicLibrary.Artist;
import nl.pi.services.MusicLibrary.Track;
import nl.pi.services.MusicLibrary.TrackStatus;
import nl.pi.services.MusicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MusicControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MusicService musicService;

    @MockitoBean
    private CommandService commandService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void returnsNestedRecordsAndTrackStatusesWithoutUsingCommands() throws Exception {
        var tracks = List.of(
                new Track("Title", "track.mp3", TrackStatus.OK, List.of()),
                new Track("missing.mp3", "missing.mp3", TrackStatus.MISSING_TAGS, List.of("TITLE")),
                new Track("broken.mp3", "broken.mp3", TrackStatus.READ_ERROR, List.of()));
        when(musicService.getMusic()).thenReturn(new MusicLibrary(
                List.of(new Artist("Artist", List.of(new Album("Album", tracks))))));

        mvc.perform(get("/api/music").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.artists[0].name").value("Artist"))
                .andExpect(jsonPath("$.artists[0].albums[0].name").value("Album"))
                .andExpect(jsonPath("$.artists[0].albums[0].tracks[0].title").value("Title"))
                .andExpect(jsonPath("$.artists[0].albums[0].tracks[0].path").value("track.mp3"))
                .andExpect(jsonPath("$.artists[0].albums[0].tracks[0].status").value("OK"))
                .andExpect(jsonPath("$.artists[0].albums[0].tracks[1].status").value("MISSING_TAGS"))
                .andExpect(jsonPath("$.artists[0].albums[0].tracks[1].missingTags[0]").value("TITLE"))
                .andExpect(jsonPath("$.artists[0].albums[0].tracks[2].status").value("READ_ERROR"));

        verify(musicService).getMusic();
        verifyNoInteractions(commandService);
    }

    @Test
    void requiresAuthenticationWithoutScanningTheDirectory() throws Exception {
        mvc.perform(get("/api/music")).andExpect(status().isUnauthorized());
        verifyNoInteractions(musicService, commandService);
    }

    @Test
    void returnsAnEmptyArtistList() throws Exception {
        when(musicService.getMusic()).thenReturn(new MusicLibrary(List.of()));
        mvc.perform(get("/api/music").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"artists\":[]}"));
    }

    @Test
    void reportsDirectoryReadFailuresExplicitly() throws Exception {
        when(musicService.getMusic()).thenThrow(new IOException("Muziekmap niet leesbaar"));
        mvc.perform(get("/api/music").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "De muziekmap kon niet worden gelezen. Controleer de logs op de Raspberry Pi."));
    }

    @Test
    void rejectsPostRequestsWithoutScanningTheDirectory() throws Exception {
        mvc.perform(post("/api/music").with(jwt())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(musicService, commandService);
    }
}
