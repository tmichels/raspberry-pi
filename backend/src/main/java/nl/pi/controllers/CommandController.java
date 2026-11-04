package nl.pi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import nl.pi.services.CommandService;
import nl.pi.services.CommandResult;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commands")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "Commando afgerond; ook een niet-nul exitcode geeft HTTP 200.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = CommandResult.class))),
        @ApiResponse(responseCode = "401", description = "Access token ontbreekt of is ongeldig; geen uitvoering.",
                content = @Content),
        @ApiResponse(responseCode = "415", description = "Content-Type is niet application/json.",
                content = @Content),
        @ApiResponse(responseCode = "500", description = "Uitvoeringsfout of time-out; zie de serverlogs.",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
})
public class CommandController {

    private static final String TOUCH_COMMAND = "touch \"$(date \"+%F %T\")\"";
    private static final String MUSIC_DIRECTORIES_COMMAND =
            "find /home/thomas/muziek/ -mindepth 2 -type d -printf '%P\\n'";

    private final CommandService commandService;

    @PostMapping(value = "/touch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Maak een bestand met de huidige datum en tijd als naam",
            description = "Werkt in de ingestelde werkmap. Een bestaand bestand met dezelfde naam wordt bijgewerkt.",
            requestBody = @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(type = "object"), examples = @ExampleObject(value = "{}"))))
    public CommandResult touch() {
        return commandService.execute(TOUCH_COMMAND);
    }

    @PostMapping(value = "/music-directories", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Toon muziekmappen vanaf twee niveaus onder de bibliotheek",
            description = "Mapnamen zijn relatief aan /home/thomas/muziek/ en gescheiden door regeleinden.",
            requestBody = @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(type = "object"), examples = @ExampleObject(value = "{}"))))
    public CommandResult musicDirectories() {
        return commandService.execute(MUSIC_DIRECTORIES_COMMAND);
    }
}
