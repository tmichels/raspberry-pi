package nl.pi.services;

import nl.pi.config.CommandProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandServiceTest {

    private static final String TOUCH_COMMAND = "touch $(date \"+%F %T\")";

    @TempDir
    Path directory;

    private CommandService service;

    @BeforeEach
    void setUp() {
        service = new CommandService(new CommandProperties(directory.resolve("files")));
    }

    @Test
    void createsSeparateDateAndTimeFilesUsingTheShell() throws IOException {
        Files.createDirectory(directory.resolve("files"));
        assertEquals(new CommandResult("", 0), service.execute(TOUCH_COMMAND));

        try (var files = Files.list(directory.resolve("files"))) {
            var paths = files.toList();
            assertEquals(2, paths.size());
            assertTrue(paths.stream().allMatch(Files::isRegularFile));
            assertEquals(1, paths.stream().filter(path -> path.getFileName().toString()
                    .matches("\\d{4}-\\d{2}-\\d{2}")).count());
            assertEquals(1, paths.stream().filter(path -> path.getFileName().toString()
                    .matches("\\d{2}:\\d{2}:\\d{2}")).count());
        }
    }

    @Test
    void createsAMissingOutputDirectory() throws IOException {
        assertEquals(new CommandResult("", 0), service.execute(TOUCH_COMMAND));

        assertTrue(Files.isDirectory(directory.resolve("files")));
    }

    @Test
    void reportsUnusableOutputDirectory() throws IOException {
        Files.createFile(directory.resolve("files"));

        var exception = assertThrows(CommandExecutionException.class, () -> service.execute(TOUCH_COMMAND));

        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void passesTheExactRequestedCommandToTheShell() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(5, TimeUnit.SECONDS)).thenReturn(true);

        try (var construction = mockConstruction(ProcessBuilder.class, withSettings().defaultAnswer(RETURNS_SELF),
                (builder, context) -> {
                    assertEquals(List.of("/bin/sh", "-c", "touch $(date \"+%F %T\")"),
                            List.of((String[]) context.arguments().getFirst()));
                    when(builder.start()).thenReturn(process);
                })) {
            service.execute(TOUCH_COMMAND);
            verify(construction.constructed().getFirst()).directory(directory.resolve("files").toFile());
            verify(process).close();
        }
    }

    @Test
    void reportsNonzeroExitCode() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(5, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(1);

        try (var construction = mockBuilder(process)) {
            assertEquals(new CommandResult("", 1), service.execute("exit 1"));
        }
    }

    @Test
    void stopsTheProcessOnTimeout() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(5, TimeUnit.SECONDS)).thenReturn(false);
        when(process.isAlive()).thenReturn(true);

        try (var construction = mockBuilder(process)) {
            assertThrows(CommandExecutionException.class, () -> service.execute("sleep 10"));
            var order = inOrder(process);
            order.verify(process).destroyForcibly();
            order.verify(process).close();
        }
    }

    @Test
    void preservesInterruptAndStopsTheProcess() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        when(process.isAlive()).thenReturn(true);

        try (var construction = mockBuilder(process)) {
            assertThrows(CommandExecutionException.class, () -> service.execute("sleep 10"));
            assertTrue(Thread.currentThread().isInterrupted());
            var order = inOrder(process);
            order.verify(process).destroyForcibly();
            order.verify(process).close();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void reportsFailureToStartTheShell() {
        try (var construction = mockConstruction(ProcessBuilder.class, withSettings().defaultAnswer(RETURNS_SELF),
                (builder, context) -> when(builder.start()).thenThrow(new IOException("shell ontbreekt")))) {
            var exception = assertThrows(CommandExecutionException.class, () -> service.execute("pwd"));
            assertInstanceOf(IOException.class, exception.getCause());
        }
    }

    @Test
    void executesAnotherCommandInTheConfiguredDirectory() throws IOException {
        Files.createDirectory(directory.resolve("files"));

        service.execute("printf 'hello' > message.txt");

        assertEquals("hello", Files.readString(directory.resolve("files/message.txt")));
    }

    @Test
    void reportsAnActualCommandFailure() throws IOException {
        Files.createDirectory(directory.resolve("files"));

        assertEquals(new CommandResult("error\n", 7), service.execute("printf 'error\\n' >&2; exit 7"));
    }

    @Test
    void returnsCombinedStandardAndErrorOutput() throws IOException {
        Files.createDirectory(directory.resolve("files"));

        var result = service.execute("printf 'hello\\n'; printf 'warning\\n' >&2");

        assertEquals(new CommandResult("hello\nwarning\n", 0), result);
    }

    @Test
    void handlesOutputLargerThanAPipeBuffer() throws IOException {
        Files.createDirectory(directory.resolve("files"));

        var result = service.execute("head -c 200000 /dev/zero | tr '\\000' x");

        assertEquals(0, result.exitCode());
        assertEquals("x".repeat(200000), result.output());
    }

    private MockedConstruction<ProcessBuilder> mockBuilder(Process process) {
        return mockConstruction(ProcessBuilder.class, withSettings().defaultAnswer(RETURNS_SELF),
                (builder, context) -> when(builder.start()).thenReturn(process));
    }

}
