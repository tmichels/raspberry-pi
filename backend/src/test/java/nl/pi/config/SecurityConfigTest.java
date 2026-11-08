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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.anyString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final RSAKey KEY = generateKey();
    private static final HttpServer JWKS_SERVER = startJwksServer();
    private static final String ISSUER = "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/realms/donna";
    private static final String AUDIO = "0123456789";
    private static final Path MUSIC_DIRECTORY = createMusicDirectory();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CommandService commandService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> ISSUER + "/certs");
        registry.add("pi.music.directory", MUSIC_DIRECTORY::toString);
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

    @Test
    void streamsAudioWithAValidSignedToken() throws Exception {
        var bearer = "Bearer " + token(KEY, ISSUER, "pi", Instant.now().plusSeconds(300));
        mvc.perform(get("/api/music/stream").param("path", "Artist/Album/track.mp3")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(header().string("Content-Length", "10"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().doesNotExist("Content-Range"))
                .andExpect(content().string(AUDIO));
        streamRange(bearer, "bytes=0-", "bytes 0-9/10", AUDIO);
        streamRange(bearer, "bytes=2-5", "bytes 2-5/10", "2345");
    }

    @Test
    void rejectsAudioStreamsWithoutOrWithInvalidBearerToken() throws Exception {
        mvc.perform(get("/api/music/stream").param("path", "Artist/Album/track.mp3"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
        mvc.perform(get("/api/music/stream").param("path", "Artist/Album/track.mp3")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
        mvc.perform(get("/api/music/stream").param("path", "Artist/Album/track.mp3")
                        .header("Authorization", "Bearer " + token(generateKey(), ISSUER, "pi",
                                Instant.now().plusSeconds(300))))
                .andExpect(status().isUnauthorized());
    }

    private void streamRange(String bearer, String range, String contentRange, String body) throws Exception {
        mvc.perform(get("/api/music/stream").param("path", "Artist/Album/track.mp3")
                        .header("Authorization", bearer).header("Range", range))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(header().string("Content-Range", contentRange))
                .andExpect(header().string("Content-Length", Integer.toString(body.length())))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().string(body));
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

    private static Path createMusicDirectory() {
        try {
            var directory = Files.createTempDirectory("security-config-music");
            var album = Files.createDirectories(directory.resolve("Artist/Album"));
            Files.writeString(album.resolve("track.mp3"), AUDIO);
            return directory;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create test music directory", exception);
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
