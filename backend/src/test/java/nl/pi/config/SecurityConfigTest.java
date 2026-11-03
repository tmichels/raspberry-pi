package nl.pi.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import nl.pi.services.CommandResult;
import nl.pi.services.CommandService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.anyString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final RSAKey KEY = generateKey();
    private static final HttpServer JWKS_SERVER = startJwksServer();
    private static final String ISSUER = "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/realms/donna";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CommandService commandService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> ISSUER + "/certs");
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Test
    void rejectsRequestsWithoutTokensOrWithMalformedTokens() throws Exception {
        mvc.perform(post("/api/commands/touch").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
        requestWithToken("invalid-token", 401);
        verifyNoInteractions(commandService);
    }

    @Test
    void acceptsAValidSignedTokenWithoutCreatingASession() throws Exception {
        when(commandService.execute(anyString())).thenReturn(new CommandResult("", 0));
        mvc.perform(post("/api/commands/touch")
                        .header("Authorization", "Bearer " + token(KEY, ISSUER, "pi", Instant.now().plusSeconds(300)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(jsonPath("$.exitCode").value(0));
        verify(commandService).execute("touch \"$(date \"+%F %T\")\"");
    }

    @Test
    void rejectsExpiredTokensAndTokensForAnotherRealmOrAudience() throws Exception {
        requestWithToken(token(KEY, ISSUER, "pi", Instant.now().minusSeconds(120)), 401);
        requestWithToken(token(KEY, ISSUER + "-other", "pi", Instant.now().plusSeconds(300)), 401);
        requestWithToken(token(KEY, ISSUER, "other-client", Instant.now().plusSeconds(300)), 401);
        verifyNoInteractions(commandService);
    }

    @Test
    void rejectsTokensSignedByAnotherKey() throws Exception {
        requestWithToken(token(generateKey(), ISSUER, "pi", Instant.now().plusSeconds(300)), 401);
        verifyNoInteractions(commandService);
    }

    private void requestWithToken(String token, int expectedStatus) throws Exception {
        mvc.perform(post("/api/commands/touch").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(expectedStatus));
    }

    private static String token(RSAKey key, String issuer, String audience, Instant expiration) throws JOSEException {
        var claims = new JWTClaimsSet.Builder()
                .subject("donna-user")
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(300)))
                .expirationTime(Date.from(expiration))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Cannot generate test signing key", exception);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] response = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/realms/donna/certs", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start test JWKS server", exception);
        }
    }
}
