package nl.pi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import nl.pi.services.MusicLibrary;
import nl.pi.services.MusicService;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Toon alle muziektracks gegroepeerd per artiest en album",
            description = "Leest audiotags uit de ingestelde muziekmap, inclusief submappen. "
                    + "Trackstatus: OK, MISSING_TAGS (met missingTags) of READ_ERROR.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Muziekbibliotheek opgehaald.",
                    content = @Content(schema = @Schema(implementation = MusicLibrary.class))),
            @ApiResponse(responseCode = "401", description = "Access token ontbreekt of is ongeldig.",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Muziekmap kon niet worden gelezen.",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public MusicLibrary getMusic() throws IOException {
        return musicService.getMusic();
    }
}
