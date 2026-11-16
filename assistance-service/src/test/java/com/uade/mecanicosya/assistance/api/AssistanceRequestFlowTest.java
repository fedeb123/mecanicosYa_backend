package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.application.AssistanceFacade;
import com.uade.mecanicosya.assistance.application.ClientFacade;
import com.uade.mecanicosya.assistance.application.DispatchCommand;
import com.uade.mecanicosya.assistance.application.DispatchOutcome;
import com.uade.mecanicosya.assistance.application.DispatchPort;
import com.uade.mecanicosya.assistance.application.OfferExpiryJob;
import com.uade.mecanicosya.assistance.domain.AssistanceRepository;
import com.uade.mecanicosya.assistance.domain.Client;
import com.uade.mecanicosya.assistance.domain.Vehicle;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import com.uade.mecanicosya.shared.messaging.OutboxMessage;
import com.uade.mecanicosya.shared.messaging.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * El pedido de asistencia de punta a punta por HTTP, contra un Postgres real: la idempotencia depende de la
 * restricción única de la base. Si no hay Docker, el test se saltea.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Kafka se prueba en AssistanceKafkaIntegrationTest; acá alcanza con ver qué queda en el outbox.
                "messaging.outbox.relay.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false"
        }
)
@Testcontainers(disabledWithoutDocker = true)
// Cada clase levanta sus contenedores: el contexto se cierra con ellos para que no queden tareas programadas
// intentando hablar con una base que ya no existe.
@DirtiesContext
class AssistanceRequestFlowTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    @MockitoBean
    DispatchPort dispatchPort;

    @Autowired
    ClientFacade clientFacade;

    @Autowired
    AssistanceRepository repository;

    @Autowired
    OfferExpiryJob offerExpiryJob;

    @Autowired
    AssistanceFacade assistanceFacade;

    @Autowired
    OutboxMessageRepository outbox;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private Client client;
    private Vehicle vehicle;

    @BeforeEach
    void aRiderWithABike() {
        client = clientFacade.register("Rider", "+54 11 5555-0000", UUID.randomUUID() + "@example.com");
        vehicle = clientFacade.registerVehicle(client.getId(), VehicleType.BICYCLE, "Trek", "FX 2", null, null);
        when(dispatchPort.dispatch(any(DispatchCommand.class)))
                .thenReturn(new DispatchOutcome.Offered(Instant.now().plus(Duration.ofMinutes(2)), 3));
    }

    @Test
    void aRetryWithTheSameKeyReturnsTheSameAssistance() throws Exception {
        HttpResponse<String> first = create("key-1", null);
        HttpResponse<String> retry = create("key-1", null);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(idOf(retry)).isEqualTo(idOf(first));
        assertThat(first.body()).contains("\"status\":\"REQUESTED\"");
        assertThat(repository.findByClient_IdOrderByCreatedAtDesc(client.getId())).hasSize(1);
        assertThat(eventsOf(idOf(first))).containsExactly("ASSISTANCE_REQUESTED");
    }

    @Test
    void fiveSimultaneousRetriesCreateASingleAssistance() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> attempts = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            for (int index = 0; index < 5; index++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return create("key-race", null);
                }));
            }
            start.countDown();
        }

        List<Integer> statuses = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Future<HttpResponse<String>> attempt : attempts) {
            statuses.add(attempt.get().statusCode());
            ids.add(idOf(attempt.get()));
        }
        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 200);
        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(repository.findByClient_IdOrderByCreatedAtDesc(client.getId())).hasSize(1);
        assertThat(eventsOf(ids.getFirst())).containsExactly("ASSISTANCE_REQUESTED");
    }

    @Test
    void theIdempotencyKeyIsRequired() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/assistances"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(null))));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void aRequestQueuedForTooLongOnThePhoneIsRejected() throws Exception {
        HttpResponse<String> response = create("key-old", Instant.now().minus(Duration.ofMinutes(20)));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("REQUEST_EXPIRED");
        assertThat(repository.findByClient_IdOrderByCreatedAtDesc(client.getId())).isEmpty();
    }

    @Test
    void theAcceptedMechanicIsAssignedOnceAndAnotherOneIsRejected() throws Exception {
        String id = idOf(create("key-assign", Instant.now().minusSeconds(10)));
        UUID mechanicId = UUID.randomUUID();

        assertThat(assign(id, mechanicId).statusCode()).isEqualTo(200);
        HttpResponse<String> repeated = assign(id, mechanicId);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(repeated.body()).contains("\"status\":\"MATCHED\"").contains(mechanicId.toString());
        assertThat(assign(id, UUID.randomUUID()).statusCode()).isEqualTo(409);

        HttpResponse<String> jobs = send(HttpRequest.newBuilder(uri("/api/assistances?mechanicId=" + mechanicId)).GET());
        assertThat(jobs.body()).contains(id);
    }

    @Test
    void anAssistanceNobodyTookBecomesUnmatchedAndCanBePublishedAgain() throws Exception {
        String id = idOf(create("key-expired", null));
        assistanceFacade.awaitOffers(UUID.fromString(id), Instant.now().minusSeconds(1));

        offerExpiryJob.expireOffers();
        HttpResponse<String> unmatched = send(HttpRequest.newBuilder(uri("/api/assistances/" + id)).GET());
        assertThat(unmatched.body()).contains("\"status\":\"UNMATCHED\"");
        assertThat(assign(id, UUID.randomUUID()).statusCode()).isEqualTo(409);

        when(dispatchPort.dispatch(any(DispatchCommand.class)))
                .thenReturn(new DispatchOutcome.Offered(Instant.now().plus(Duration.ofMinutes(2)), 2));
        HttpResponse<String> republished = send(HttpRequest.newBuilder(uri("/api/assistances/" + id + "/dispatch"))
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(republished.body()).contains("\"status\":\"REQUESTED\"");
    }

    @Test
    void cancellingTwiceAnswersTheSameAndWithdrawsTheOffersOnce() throws Exception {
        String id = idOf(create("key-cancel", null));
        HttpRequest.Builder cancel = HttpRequest.newBuilder(uri("/api/assistances/" + id + "/cancel"))
                .POST(HttpRequest.BodyPublishers.noBody());

        assertThat(send(cancel).statusCode()).isEqualTo(200);
        assertThat(send(cancel).body()).contains("\"status\":\"CANCELLED\"");
        assertThat(eventsOf(id)).containsExactly("ASSISTANCE_REQUESTED", "ASSISTANCE_CANCELLED");
    }

    @Test
    void thePhoneCancelsByKeyARequestItDoesNotKnowArrived() throws Exception {
        String id = idOf(create("key-offline-cancel", null));

        HttpResponse<String> cancelled = cancelByKey("key-offline-cancel");
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(cancelled.body()).contains("\"id\":\"" + id + "\"", "\"status\":\"CANCELLED\"");
        assertThat(cancelByKey("key-offline-cancel").statusCode()).isEqualTo(200);

        HttpResponse<String> neverArrived = cancelByKey("key-never-arrived");
        assertThat(neverArrived.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> create(String key, Instant clientCreatedAt) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri("/api/assistances"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body(clientCreatedAt))));
    }

    private List<String> eventsOf(String assistanceId) {
        return outbox.findByTopicOrderByIdAsc("assistance.events").stream()
                .filter(message -> message.getMessageKey().equals(assistanceId))
                .map(OutboxMessage::getEventType)
                .toList();
    }

    private HttpResponse<String> cancelByKey(String key) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri("/api/assistances/cancel-by-key?clientId=" + client.getId()))
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.noBody()));
    }

    private HttpResponse<String> assign(String id, UUID mechanicId) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri("/api/assistances/" + id + "/assign"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"mechanicId\":\"" + mechanicId + "\"}")));
    }

    private String body(Instant clientCreatedAt) {
        return """
                {"clientId":"%s","vehicleId":"%s","description":"Se cortó la cadena","problemType":"CHAIN",
                 "requiredSkill":"CHAIN_REPAIR","latitude":-34.6037,"longitude":-58.3816%s}
                """.formatted(
                client.getId(),
                vehicle.getId(),
                clientCreatedAt == null ? "" : ",\"clientCreatedAt\":\"" + clientCreatedAt + "\""
        );
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String idOf(HttpResponse<String> response) {
        Matcher matcher = ID.matcher(response.body());
        assertThat(matcher.find()).as("id in %s", response.body()).isTrue();
        return matcher.group(1);
    }
}
