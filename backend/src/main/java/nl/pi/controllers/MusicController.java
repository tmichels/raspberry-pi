package nl.pi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import nl.pi.services.music.MusicLibrary;
import nl.pi.services.music.MusicService;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            description = "Indeling van artiest en album  en overige metadata is op basis van id3v2 tags van de audiobestanden.")
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

    @GetMapping(value = "/album-art", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "Haal de albumhoes op voor een albummap",
            description = "Gebruik het relatieve albumpad uit de muziekbibliotheek.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Albumhoes opgehaald.",
                    content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE)),
            @ApiResponse(responseCode = "400", description = "Ongeldig albumpad.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Access token ontbreekt of is ongeldig.", content = @Content),
            @ApiResponse(responseCode = "404", description = "Albumhoes niet gevonden.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Albumhoes kon niet worden gelezen.",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<byte[]> getAlbumArt(@RequestParam("path") String path) throws IOException {
        return musicService.getAlbumArt(path)
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
