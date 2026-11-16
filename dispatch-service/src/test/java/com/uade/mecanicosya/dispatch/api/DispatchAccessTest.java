package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.application.AssistanceAssignmentPort;
import com.uade.mecanicosya.dispatch.application.MechanicDirectory;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.shared.security.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ofertas y aceptación por HTTP con tokens firmados: sólo un mecánico aprobado ve sus ofertas y acepta, y siempre
 * en su propio nombre. Si no hay Docker, se saltea.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "messaging.outbox.relay.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false"
        }
)
@Import(DispatchAccessTest.Tokens.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class DispatchAccessTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @TestConfiguration
    static class Tokens {
        @Bean
        JwtDecoder jwtDecoder() {
            return TestJwt.decoder();
        }
    }

    @MockitoBean
    MechanicDirectory mechanicDirectory;

    @MockitoBean
    AssistanceAssignmentPort assignmentPort;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final UUID ada = UUID.randomUUID();
    private final UUID grace = UUID.randomUUID();
    private UUID assistanceId;

    @BeforeEach
    void anAssistanceOfferedToTwoMechanics() throws Exception {
        when(mechanicDirectory.findCandidates(eq("CHAIN_REPAIR"), any())).thenReturn(List.of(
                new MechanicCandidate(ada, "Ada", "ada@example.com", -34.6037, -58.3816, true, 4.5, Set.of("CHAIN_REPAIR")),
                new MechanicCandidate(grace, "Grace", "grace@example.com", -34.6040, -58.3820, true, 4.8, Set.of("CHAIN_REPAIR"))
        ));
        assistanceId = UUID.randomUUID();
        HttpResponse<String> offered = send("POST", "/api/dispatches", """
                {"assistanceId":"%s","requiredSkill":"CHAIN_REPAIR","latitude":-34.6037,"longitude":-58.3816}
                """.formatted(assistanceId), TestJwt.service("assistance-service"));
        assertThat(offered.statusCode()).isEqualTo(200);
        assertThat(offered.body()).contains("\"status\":\"OFFERED\"");
    }

    @Test
    void anApprovedMechanicSeesTheirOffersAndAcceptsInTheirOwnName() throws Exception {
        String adaToken = TestJwt.approvedMechanic(ada);

        HttpResponse<String> offers = send("GET", "/api/dispatches/offers", null, adaToken);
        assertThat(offers.statusCode()).isEqualTo(200);
        assertThat(offers.body()).contains(assistanceId.toString());

        // Ada no puede aceptar en nombre de Grace.
        assertThat(send("POST", "/api/dispatches/" + assistanceId + "/accept",
                "{\"mechanicId\":\"" + grace + "\"}", adaToken).statusCode()).isEqualTo(403);
        verify(assignmentPort, never()).assign(any(), any());

        HttpResponse<String> accepted = send("POST", "/api/dispatches/" + assistanceId + "/accept", null, adaToken);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body()).contains("\"status\":\"MATCHED\"").contains(ada.toString());
        verify(assignmentPort).assign(assistanceId, ada);

        assertThat(send("POST", "/api/dispatches/" + assistanceId + "/accept", null, TestJwt.approvedMechanic(grace))
                .statusCode()).isEqualTo(409);
    }

    @Test
    void aMechanicPendingVerificationGets403() throws Exception {
        String pending = TestJwt.pendingMechanic(ada);

        HttpResponse<String> offers = send("GET", "/api/dispatches/offers", null, pending);
        assertThat(offers.statusCode()).isEqualTo(403);
        assertThat(offers.body()).contains("pending verification");
        assertThat(send("POST", "/api/dispatches/" + assistanceId + "/accept", null, pending).statusCode())
                .isEqualTo(403);
    }

    @Test
    void eachEndpointChecksTheRole() throws Exception {
        String rider = TestJwt.rider(UUID.randomUUID());

        assertThat(send("GET", "/api/dispatches/offers", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/dispatches/offers", null, rider).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/dispatches/" + assistanceId, null, rider).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/dispatches/" + assistanceId, null, TestJwt.approvedMechanic(ada)).statusCode())
                .isEqualTo(403);
        assertThat(send("POST", "/api/dispatches/" + assistanceId + "/cancel", null, rider).statusCode())
                .isEqualTo(403);
        assertThat(send("GET", "/api/dispatches/" + assistanceId, null, TestJwt.admin()).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/dispatches/offers?mechanicId=" + ada, null, TestJwt.admin()).body())
                .contains(assistanceId.toString());
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
