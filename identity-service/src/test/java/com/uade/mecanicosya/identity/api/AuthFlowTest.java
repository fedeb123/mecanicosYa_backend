package com.uade.mecanicosya.identity.api;

import com.uade.mecanicosya.identity.application.AuthFacade;
import com.uade.mecanicosya.identity.domain.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alta, login, renovación y tokens de servicio por HTTP, contra un Postgres real. Si no hay Docker, se saltea.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class AuthFlowTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    AuthFacade authFacade;

    @Autowired
    JsonMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aRiderSignsUpLogsInAndSeesTheirAccount() throws Exception {
        String email = unique();
        HttpResponse<String> registered = post("/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"secret-123\",\"role\":\"RIDER\"}");
        HttpResponse<String> login = post("/api/auth/login", "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"secret-123\"}");

        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode session = json.readTree(login.body());
        assertThat(session.get("role").asString()).isEqualTo("RIDER");
        assertThat(session.get("verificationStatus").isNull()).isTrue();
        assertThat(session.get("userId").asString()).isEqualTo(json.readTree(registered.body()).get("userId").asString());

        Jwt token = jwtDecoder.decode(session.get("accessToken").asString());
        assertThat(token.getSubject()).isEqualTo(session.get("userId").asString());
        assertThat(token.getClaimAsString("role")).isEqualTo("RIDER");
        assertThat(token.getClaimAsString("verification_status")).isNull();

        HttpResponse<String> me = get("/api/auth/me", session.get("accessToken").asString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"email\":\"" + email + "\"");
    }

    @Test
    void wrongPasswordUnknownEmailAndDuplicateSignUpAreRejected() throws Exception {
        String email = unique();
        post("/api/auth/register", "{\"email\":\"" + email + "\",\"password\":\"secret-123\",\"role\":\"RIDER\"}");

        assertThat(post("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}").statusCode())
                .isEqualTo(401);
        assertThat(post("/api/auth/login", "{\"email\":\"" + unique() + "\",\"password\":\"secret-123\"}").statusCode())
                .isEqualTo(401);
        assertThat(post("/api/auth/register", "{\"email\":\"" + email + "\",\"password\":\"other-123\",\"role\":\"MECHANIC\"}")
                .statusCode()).isEqualTo(409);
        assertThat(post("/api/auth/register", "{\"email\":\"" + unique() + "\",\"password\":\"secret-123\",\"role\":\"ADMIN\"}")
                .statusCode()).isEqualTo(400);
        assertThat(get("/api/auth/me", null).statusCode()).isEqualTo(401);
    }

    @Test
    void aMechanicStartsPendingAndTheRefreshAfterApprovalSaysApproved() throws Exception {
        JsonNode session = json.readTree(post("/api/auth/register",
                "{\"email\":\"" + unique() + "\",\"password\":\"secret-123\",\"role\":\"MECHANIC\"}").body());
        assertThat(session.get("verificationStatus").asString()).isEqualTo("PENDING_VERIFICATION");
        assertThat(jwtDecoder.decode(session.get("accessToken").asString()).getClaimAsString("verification_status"))
                .isEqualTo("PENDING_VERIFICATION");

        authFacade.changeVerification(UUID.fromString(session.get("userId").asString()), VerificationStatus.APPROVED);
        String refreshToken = session.get("refreshToken").asString();
        HttpResponse<String> refreshed = post("/api/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");

        assertThat(refreshed.statusCode()).isEqualTo(200);
        JsonNode renewed = json.readTree(refreshed.body());
        assertThat(renewed.get("verificationStatus").asString()).isEqualTo("APPROVED");
        assertThat(jwtDecoder.decode(renewed.get("accessToken").asString()).getClaimAsString("verification_status"))
                .isEqualTo("APPROVED");
        // Rotación: el refresh token usado ya no sirve, el nuevo sí.
        assertThat(post("/api/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}").statusCode()).isEqualTo(401);
        assertThat(renewed.get("refreshToken").asString()).isNotEqualTo(refreshToken);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = json.readTree(post("/api/auth/register",
                "{\"email\":\"" + unique() + "\",\"password\":\"secret-123\",\"role\":\"RIDER\"}").body())
                .get("refreshToken").asString();

        assertThat(post("/api/auth/logout", "{\"refreshToken\":\"" + refreshToken + "\"}").statusCode()).isEqualTo(204);
        assertThat(post("/api/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}").statusCode()).isEqualTo(401);
    }

    @Test
    void theInitialAdminCanLogIn() throws Exception {
        HttpResponse<String> login = post("/api/auth/login",
                "{\"email\":\"admin@mecanicosya.test\",\"password\":\"admin-test-password\"}");

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(json.readTree(login.body()).get("role").asString()).isEqualTo("ADMIN");
    }

    @Test
    void aServiceGetsATokenOnlyWithItsSecret() throws Exception {
        HttpResponse<String> issued = post("/api/auth/service-token",
                "{\"clientId\":\"dispatch-service\",\"clientSecret\":\"dispatch-test-secret\"}");

        assertThat(issued.statusCode()).isEqualTo(200);
        Jwt token = jwtDecoder.decode(json.readTree(issued.body()).get("accessToken").asString());
        assertThat(token.getSubject()).isEqualTo("dispatch-service");
        assertThat(token.getClaimAsString("role")).isEqualTo("SERVICE");

        assertThat(post("/api/auth/service-token",
                "{\"clientId\":\"dispatch-service\",\"clientSecret\":\"wrong\"}").statusCode()).isEqualTo(401);
        // Un secreto vacío en la configuración deja al servicio sin token, aunque mande vacío.
        assertThat(post("/api/auth/service-token",
                "{\"clientId\":\"assistance-service\",\"clientSecret\":\" \"}").statusCode()).isIn(400, 401);
    }

    @Test
    void publishesThePublicKey() throws Exception {
        HttpResponse<String> keys = get("/.well-known/jwks.json", null);

        assertThat(keys.statusCode()).isEqualTo(200);
        JsonNode key = json.readTree(keys.body()).get("keys").get(0);
        assertThat(key.get("kty").asString()).isEqualTo("RSA");
        assertThat(key.has("d")).as("the private exponent must never be published").isFalse();
    }

    private static String unique() {
        return UUID.randomUUID() + "@example.com";
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
