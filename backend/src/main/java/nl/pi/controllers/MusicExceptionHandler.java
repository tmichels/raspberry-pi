package nl.pi.controllers;

import lombok.extern.slf4j.Slf4j;
import nl.pi.repositories.InvalidAlbumPathException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@Slf4j
@RestControllerAdvice(assignableTypes = MusicController.class)
public class MusicExceptionHandler {

    @ExceptionHandler(InvalidAlbumPathException.class)
    public ProblemDetail handleInvalidAlbumPath() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Het albumpad is ongeldig.");
    }

    @ExceptionHandler(IOException.class)
    public ProblemDetail handleMusicFailure(IOException exception) {
        log.error("Ophalen van muziekgegevens mislukt", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "De muziekmap kon niet worden gelezen. Controleer de logs op de Raspberry Pi.");
    }
}
