package nl.pi.services;

import io.swagger.v3.oas.annotations.media.Schema;

public record CommandResult(
        @Schema(description = "Gecombineerde standaardoutput en foutoutput.", requiredMode = Schema.RequiredMode.REQUIRED)
        String output,
        @Schema(description = "Exitcode van het commando; nul betekent succes.", requiredMode = Schema.RequiredMode.REQUIRED)
        int exitCode) {
}
