package nl.pi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Raspberry Pi backend")
                        .version("1.0")
                        .description("Vaste Linux-acties zonder commando-invoer van de client. Commando's worden na maximaal vijf seconden gestopt."))
                .addServersItem(new Server().url("/"))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Keycloak access token uit realm donna met audience pi; geen ID token.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
