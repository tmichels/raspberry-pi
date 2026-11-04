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
    private static final String MUSIC_DIRECTORIES_COMMAND =
            "find /home/thomas/muziek/ -mindepth 2 -type d -printf '%P\\n'";

    private final CommandService commandService;

    @PostMapping(value = "/touch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommandResult touch() {
        return commandService.execute(TOUCH_COMMAND);
    }

    @PostMapping(value = "/music-directories", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommandResult musicDirectories() {
        return commandService.execute(MUSIC_DIRECTORIES_COMMAND);
    }
}
