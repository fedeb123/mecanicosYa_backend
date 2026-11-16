package com.uade.mecanicosya.dispatch.infrastructure;

import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.dispatch.domain.RouteEstimate;
import com.uade.mecanicosya.dispatch.domain.RouteServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba el cliente contra un servidor HTTP local que responde como la Matrix API de OpenRouteService. */
class OpenRouteServiceClientTest {

    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> contentLength = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody;
    private volatile long delayMillis;
    private HttpServer server;
    private OpenRouteServiceClient client;

    private final MechanicCandidate ada = mechanic(-34.5800, -58.4255);
    private final MechanicCandidate grace = mechanic(-34.5840, -58.4300);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new OpenRouteServiceClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key", "driving-car", Duration.ofMillis(500));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void asksOneMatrixFromEveryMechanicToTheRequest() {
        responseBody = """
                {"durations":[[312.4],[540.0]],"distances":[[1.92],[3.05]],
                 "metadata":{"service":"matrix"}}""";

        List<RouteEstimate> estimates = client.estimate(-34.5795, -58.4250, List.of(ada, grace));

        assertThat(estimates).containsExactly(
                new RouteEstimate(1.92, 6, EtaSource.ROUTE),
                new RouteEstimate(3.05, 9, EtaSource.ROUTE)
        );
        assertThat(path.get()).isEqualTo("/v2/matrix/driving-car");
        assertThat(authorization.get()).isEqualTo("test-key");
        assertThat(contentLength.get()).isEqualTo(String.valueOf(requestBody.get().length()));
        JsonNode sent = JsonMapper.builder().build().readTree(requestBody.get());
        // Longitud primero, como pide ORS; el pedido es el último punto.
        assertThat(sent.get("locations").toString())
                .isEqualTo("[[-58.4255,-34.58],[-58.43,-34.584],[-58.425,-34.5795]]");
        assertThat(sent.get("sources").toString()).isEqualTo("[0,1]");
        assertThat(sent.get("destinations").toString()).isEqualTo("[2]");
        assertThat(sent.get("units").asString()).isEqualTo("km");
        assertThat(requestBody.get()).doesNotContain(ada.id().toString(), "Ada");
    }

    @Test
    void aMechanicWithoutARouteComesBackEmpty() {
        responseBody = """
                {"durations":[[312.4],[null]],"distances":[[1.92],[null]]}""";

        List<RouteEstimate> estimates = client.estimate(-34.5795, -58.4250, List.of(ada, grace));

        assertThat(estimates.get(0)).isNotNull();
        assertThat(estimates.get(1)).isNull();
    }

    @Test
    void anErrorIsReportedAsUnavailable() {
        status = 403;
        responseBody = "{\"error\":\"Access to this API has been disallowed\"}";

        assertThatThrownBy(() -> client.estimate(-34.5795, -58.4250, List.of(ada)))
                .isInstanceOf(RouteServiceUnavailableException.class);
    }

    @Test
    void aSlowAnswerIsReportedAsUnavailable() {
        delayMillis = 1500;
        responseBody = "{\"durations\":[[312.4]],\"distances\":[[1.92]]}";

        assertThatThrownBy(() -> client.estimate(-34.5795, -58.4250, List.of(ada)))
                .isInstanceOf(RouteServiceUnavailableException.class);
    }

    private static MechanicCandidate mechanic(double latitude, double longitude) {
        return new MechanicCandidate(UUID.randomUUID(), "Ada", "ada@example.com", latitude, longitude, true, 4.5,
                Set.of("CHAIN_REPAIR"));
    }
}
