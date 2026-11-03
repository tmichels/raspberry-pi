package nl.pi.controllers;

import nl.pi.services.CommandExecutionException;
import nl.pi.services.CommandService;
import nl.pi.services.CommandResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
class CommandControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CommandService commandService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void returnsNotFoundForUnmappedPaths() throws Exception {
        mvc.perform(get("/index.html").with(jwt())).andExpect(status().isNotFound());
        mvc.perform(get("/").with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void executesTheActionWithAuthentication() throws Exception {
        when(commandService.execute("touch \"$(date \"+%F %T\")\"")).thenReturn(new CommandResult("", 0));

        mvc.perform(post("/api/commands/touch").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value(""))
                .andExpect(jsonPath("$.exitCode").value(0));

        verify(commandService).execute("touch \"$(date \"+%F %T\")\"");
    }

    @Test
    void returnsCommandOutputAndNonzeroExitCode() throws Exception {
        when(commandService.execute("touch \"$(date \"+%F %T\")\""))
                .thenReturn(new CommandResult("Permission denied\n", 1));

        mvc.perform(post("/api/commands/touch").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("Permission denied\n"))
                .andExpect(jsonPath("$.exitCode").value(1));
    }

    @Test
    void returnsAnExplicitErrorWhenTheCommandFails() throws Exception {
        doThrow(new CommandExecutionException("exitcode 1")).when(commandService)
                .execute("touch \"$(date \"+%F %T\")\"");

        mvc.perform(post("/api/commands/touch").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "Het commando kon niet worden uitgevoerd. Controleer de logs op de Raspberry Pi."));
    }

    @Test
    void rejectsGetRequestsAndFormSubmissionsWithoutExecutingTheAction() throws Exception {
        mvc.perform(get("/api/commands/touch").with(jwt())).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/commands/touch").with(jwt()).contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(commandService);
    }

    @Test
    void doesNotExposeAnArbitraryCommandEndpoint() throws Exception {
        mvc.perform(post("/api/commands/execute").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"pwd\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(commandService);
    }
}
