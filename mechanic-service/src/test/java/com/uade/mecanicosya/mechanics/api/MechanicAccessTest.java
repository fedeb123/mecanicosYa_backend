package com.uade.mecanicosya.mechanics.api;

import com.uade.mecanicosya.shared.messaging.OutboxMessage;
import com.uade.mecanicosya.shared.messaging.OutboxMessageRepository;
import com.uade.mecanicosya.shared.security.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quién puede hacer qué con los mecánicos, por HTTP y con tokens firmados, contra un Postgres real. Si no hay
 * Docker, se saltea.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "messaging.outbox.relay.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false"
        }
)
@Import(MechanicAccessTest.Tokens.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class MechanicAccessTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PROFILE = """
            {"name":"Ada Mecánica","latitude":-34.6037,"longitude":-58.3816,"skills":["CHAIN_REPAIR"]}""";
    private static final String AVAILABLE = """
            {"available":true,"latitude":-34.6037,"longitude":-58.3816}""";

    @TestConfiguration
    static class Tokens {
        @Bean
        JwtDecoder jwtDecoder() {
            return TestJwt.decoder();
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    OutboxMessageRepository outbox;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aNewMechanicWaitsForAnAdminBeforeGoingAvailable() throws Exception {
        UUID id = UUID.randomUUID();
        String mechanic = TestJwt.pendingMechanic(id);

        HttpResponse<String> created = send("POST", "/api/mechanics", PROFILE, mechanic);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"id\":\"" + id + "\"").contains("\"verificationStatus\":\"PENDING_VERIFICATION\"");
        assertThat(send("PATCH", "/api/mechanics/" + id + "/availability", AVAILABLE, mechanic).statusCode())
                .isEqualTo(403);
        assertThat(candidates().body()).doesNotContain(id.toString());

        assertThat(send("GET", "/api/mechanics?verificationStatus=PENDING_VERIFICATION", null, TestJwt.admin()).body())
                .contains(id.toString());
        HttpResponse<String> approved = send("POST", "/api/mechanics/" + id + "/approve", null, TestJwt.admin());
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(approved.body()).contains("\"verificationStatus\":\"APPROVED\"");
        assertThat(outbox.findAll()).filteredOn(message -> message.getMessageKey().equals(id.toString()))
                .extracting(OutboxMessage::getEventType)
                .containsExactly("MECHANIC_VERIFICATION_CHANGED");

        assertThat(send("PATCH", "/api/mechanics/" + id + "/availability", AVAILABLE, mechanic).statusCode())
                .isEqualTo(200);
        assertThat(candidates().body()).contains(id.toString());
    }

    @Test
    void onlyTheMechanicThemselfChangesTheirAvailability() throws Exception {
        UUID id = UUID.randomUUID();
        send("POST", "/api/mechanics", PROFILE, TestJwt.pendingMechanic(id));

        assertThat(send("PATCH", "/api/mechanics/" + id + "/availability", AVAILABLE,
                TestJwt.approvedMechanic(UUID.randomUUID())).statusCode()).isEqualTo(403);
    }

    @Test
    void eachEndpointChecksTheRole() throws Exception {
        UUID rider = UUID.randomUUID();

        assertThat(send("POST", "/api/mechanics", PROFILE, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/mechanics", PROFILE, "not-a-token").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/mechanics", PROFILE, TestJwt.rider(rider)).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/mechanics", null, TestJwt.approvedMechanic(rider)).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/mechanics/candidates?skill=CHAIN_REPAIR", null, TestJwt.rider(rider)).statusCode())
                .isEqualTo(403);
        assertThat(send("POST", "/api/mechanics/" + rider + "/approve", null, TestJwt.approvedMechanic(rider))
                .statusCode()).isEqualTo(403);
        assertThat(send("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    void aRiderSeesAvailableMechanicsNearbyWithoutTheirEmail() throws Exception {
        UUID id = UUID.randomUUID();
        String mechanic = TestJwt.pendingMechanic(id);
        send("POST", "/api/mechanics", PROFILE, mechanic);
        send("POST", "/api/mechanics/" + id + "/approve", null, TestJwt.admin());
        String rider = TestJwt.rider(UUID.randomUUID());
        String obelisco = "/api/mechanics/nearby?latitude=-34.6040&longitude=-58.3820";

        assertThat(send("GET", obelisco, null, rider).body()).doesNotContain(id.toString());

        send("PATCH", "/api/mechanics/" + id + "/availability", AVAILABLE, mechanic);
        HttpResponse<String> nearby = send("GET", obelisco, null, rider);
        assertThat(nearby.statusCode()).isEqualTo(200);
        assertThat(nearby.body()).contains(id.toString()).doesNotContain("email");
        assertThat(send("GET", "/api/mechanics/nearby?latitude=-34.9&longitude=-57.95", null, rider).body())
                .doesNotContain(id.toString());
        assertThat(send("GET", obelisco, null, TestJwt.approvedMechanic(id)).statusCode()).isEqualTo(403);
    }

    @Test
    void anAccountCreatesASingleProfile() throws Exception {
        String mechanic = TestJwt.pendingMechanic(UUID.randomUUID());

        assertThat(send("POST", "/api/mechanics", PROFILE, mechanic).statusCode()).isEqualTo(201);
        assertThat(send("POST", "/api/mechanics", PROFILE, mechanic).statusCode()).isEqualTo(409);
    }

    private HttpResponse<String> candidates() throws Exception {
        return send("GET", "/api/mechanics/candidates?skill=CHAIN_REPAIR", null, TestJwt.service("dispatch-service"));
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
