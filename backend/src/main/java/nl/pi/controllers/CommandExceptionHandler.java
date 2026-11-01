package nl.pi.controllers;

import lombok.extern.slf4j.Slf4j;
import nl.pi.services.CommandExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class CommandExceptionHandler {

    @ExceptionHandler(CommandExecutionException.class)
    public ProblemDetail handleCommandFailure(CommandExecutionException exception) {
        log.error("Uitvoeren van het commando mislukt", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Het commando kon niet worden uitgevoerd. Controleer de logs op de Raspberry Pi.");
    }
}
