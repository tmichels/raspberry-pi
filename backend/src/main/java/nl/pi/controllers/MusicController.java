package nl.pi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import nl.pi.services.music.MusicLibrary;
import nl.pi.services.music.MusicPlaylistService;
import nl.pi.services.music.MusicService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;
    private final MusicPlaylistService playlistService;

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

    @GetMapping(value = "/playlist", produces = "audio/x-mpegurl; charset=UTF-8")
    @SecurityRequirements
    @Operation(summary = "Open de bestaande M3U-afspeellijst van een album",
            description = "Publiek toegankelijk. Behoudt volgorde, duplicaten en EXTINF; "
                    + "lokale tracks worden absolute HTTP-stream-URL's zonder tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "UTF-8 M3U, inline aangeboden.", content = @Content),
            @ApiResponse(responseCode = "400", description = "Ongeldig pad, ambigue of ongeldige afspeellijst.",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
            @ApiResponse(responseCode = "404", description = "Album of M3U ontbreekt.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Muziekmap kon niet worden gelezen.",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    })
    public ResponseEntity<byte[]> playlist(@RequestParam("path") String path) throws IOException {
        var result = playlistService.getPlaylist(path);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var playlist = result.get();
        var stream = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/music/stream");
        var body = new StringBuilder("#EXTM3U\n");
        for (var entry : playlist.entries()) {
            if (entry.metadata() != null) {
                body.append(entry.metadata()).append('\n');
            }
            body.append(stream.cloneBuilder().queryParam("path", "{track}")
                    .encode(StandardCharsets.UTF_8).buildAndExpand(entry.trackPath()).toUriString()).append('\n');
        }
        var filename = playlist.filename().replaceAll("[\\p{Cntrl}/\\\\\"]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/x-mpegurl; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/stream")
    @SecurityRequirements
    @Operation(summary = "Stream een audiotrack",
            description = "Publiek toegankelijk zonder token. Gebruik het relatieve trackpad uit de "
                    + "muziekbibliotheek. Ondersteunt HTTP byte ranges.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Volledige audiotrack.", content = @Content),
            @ApiResponse(responseCode = "206", description = "Gevraagde byte range(s).", content = @Content),
            @ApiResponse(responseCode = "400", description = "Trackpad ontbreekt of is ongeldig.", content = @Content),
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
