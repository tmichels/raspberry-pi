package nl.pi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import nl.pi.services.music.MusicLibrary;
import nl.pi.services.music.MusicService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

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

    @GetMapping("/stream")
    @Operation(summary = "Stream een audiotrack",
            description = "Gebruik het relatieve trackpad uit de muziekbibliotheek. Ondersteunt HTTP byte ranges.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Volledige audiotrack.", content = @Content),
            @ApiResponse(responseCode = "206", description = "Gevraagde byte range(s).", content = @Content),
            @ApiResponse(responseCode = "400", description = "Trackpad ontbreekt of is ongeldig.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Access token ontbreekt of is ongeldig.", content = @Content),
            @ApiResponse(responseCode = "404", description = "Audiotrack niet gevonden in de bibliotheek.", content = @Content),
            @ApiResponse(responseCode = "416", description = "Ongeldige of niet beschikbare byte range.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Muziekbestand kon niet worden gelezen.",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Resource> stream(@RequestParam("path") String path,
                                           @RequestHeader HttpHeaders headers) throws IOException {
        var file = musicService.getAudioFile(path);
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var resource = new FileSystemResource(file.get());
        if (headers.getFirst(HttpHeaders.RANGE) != null) {
            try {
                // Valideer zoals Spring MVC, maar voorkom een resourcebody bij HTTP 416.
                HttpRange.toResourceRegions(headers.getRange(), resource);
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength())
                        .build();
            }
        }
        return ResponseEntity.ok().contentType(audioMediaType(file.get())).body(resource);
    }

    private MediaType audioMediaType(Path file) {
        var name = file.getFileName().toString();
        var extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return MediaType.parseMediaType(switch (extension) {
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        });
    }
}
