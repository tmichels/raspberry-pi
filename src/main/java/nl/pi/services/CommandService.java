package nl.pi.services;

import lombok.RequiredArgsConstructor;
import nl.pi.config.CommandProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CommandService {

    private final CommandProperties commandProperties;

    public CommandResult execute(String command) {
        var outputFile = createOutputFile();
        try {
            return executeProcess(command, outputFile);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CommandExecutionException("Het commando is onderbroken.", exception);
        } finally {
            deleteOutputFile(outputFile);
        }
    }

    private Path createOutputFile() {
        try {
            return Files.createTempFile("pi-command-", ".log");
        } catch (IOException exception) {
            throw new CommandExecutionException("Het tijdelijke outputbestand kon niet worden aangemaakt.", exception);
        }
    }

    private CommandResult executeProcess(String command, Path outputFile) throws InterruptedException {
        try (var process = new ProcessBuilder("/bin/sh", "-c", command)
                .directory(commandProperties.directory().toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start()) {
            awaitCompletion(process);
            return new CommandResult(readOutput(outputFile), process.exitValue());
        } catch (IOException exception) {
            throw new CommandExecutionException(
                    "Het proces kon niet worden gestart of gesloten in %s.".formatted(commandProperties.directory()),
                    exception);
        }
    }

    private void awaitCompletion(Process process) throws InterruptedException {
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CommandExecutionException("Het commando duurde langer dan 5 seconden.");
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            throw exception;
        }
    }

    private String readOutput(Path outputFile) {
        try {
            return Files.readString(outputFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CommandExecutionException("De commando-output kon niet worden gelezen uit %s.".formatted(outputFile), exception);
        }
    }

    private void deleteOutputFile(Path outputFile) {
        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException exception) {
            throw new CommandExecutionException("Het tijdelijke outputbestand %s kon niet worden verwijderd.".formatted(outputFile), exception);
        }
    }
}
