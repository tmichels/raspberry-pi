package nl.pi.controllers;

import lombok.RequiredArgsConstructor;
import nl.pi.services.CommandService;
import nl.pi.services.CommandResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commands")
@RequiredArgsConstructor
public class CommandController {

    private static final String TOUCH_COMMAND = "touch \"$(date \"+%F %T\")\"";

    private final CommandService commandService;

    @PostMapping(value = "/touch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommandResult touch() {
        return commandService.execute(TOUCH_COMMAND);
    }
}
