package nl.pi.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void generatesTheApiContractWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/commands/touch'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/commands/touch'].post.requestBody"
                        + ".content['application/json']").exists())
                .andExpect(jsonPath("$.paths['/api/commands/music-directories'].post.responses['500']"
                        + ".content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/music'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/MusicLibrary"))
                .andExpect(jsonPath("$.paths['/api/music'].get.responses['500']"
                        + ".content['application/problem+json']").exists())
                .andExpect(jsonPath("$.components.schemas.MusicLibrary.properties.artists.type").value("array"))
                .andExpect(jsonPath("$.components.schemas.Track.properties.status.enum[1]").value("MISSING_TAGS"))
                .andExpect(jsonPath("$.components.schemas.CommandResult.properties.output.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.CommandResult.properties.exitCode.type").value("integer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.servers[0].url").value("/"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    void servesSwaggerUiAndItsConfigurationWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/swagger-ui/index.html"));
        mvc.perform(get("/api/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/api/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v3/api-docs/swagger-config")));
        mvc.perform(get("/api/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/api/v3/api-docs"));
        mvc.perform(get("/api/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/commands/touch:")));
    }
}
