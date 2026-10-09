package com.uade.mecanicosya.assistance.infrastructure;

import com.uade.mecanicosya.assistance.application.DispatchCommand;
import com.uade.mecanicosya.assistance.application.DispatchFailedException;
import com.uade.mecanicosya.assistance.application.DispatchOutcome;
import com.uade.mecanicosya.assistance.application.DispatchUnavailableException;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchRestClientTest {

    private final UUID assistanceId = UUID.fromString("0b7e2a3c-5d6f-4a1b-8c9d-0e1f2a3b4c5d");
    private final DispatchCommand command = new DispatchCommand(
            assistanceId,
            "CHAIN_REPAIR",
            -34.6037,
            -58.3816,
            VehicleType.BICYCLE
    );

    private HttpServer server;
    private final AtomicReference<String> receivedCorrelationId = new AtomicReference<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        MDC.clear();
    }

    @Test
    void readsAMatchedAnswer() throws IOException {
        startServer(200, """
                {"assistanceId":"0b7e2a3c-5d6f-4a1b-8c9d-0e1f2a3b4c5d",
                 "mechanicId":"6f1c8f4e-1b7a-4c1e-9b1a-2a8c9d0e1f23",
                 "distanceKm":0.4,"status":"MATCHED","createdAt":"2026-09-28T12:00:00Z"}
                """, Duration.ZERO);

        DispatchOutcome outcome = client(baseUrl(), Duration.ofSeconds(2)).dispatch(command);

        assertThat(outcome).isEqualTo(new DispatchOutcome.Matched(
                UUID.fromString("6f1c8f4e-1b7a-4c1e-9b1a-2a8c9d0e1f23"),
                0.4
        ));
    }

    @Test
    void readsANoCandidateAnswer() throws IOException {
        startServer(200, """
                {"assistanceId":"0b7e2a3c-5d6f-4a1b-8c9d-0e1f2a3b4c5d","status":"NO_CANDIDATE"}
                """, Duration.ZERO);

        DispatchOutcome outcome = client(baseUrl(), Duration.ofSeconds(2)).dispatch(command);

        assertThat(outcome).isInstanceOf(DispatchOutcome.NoCandidate.class);
    }

    @Test
    void anUnknownStatusIsAFailedDispatch() throws IOException {
        startServer(200, """
                {"assistanceId":"0b7e2a3c-5d6f-4a1b-8c9d-0e1f2a3b4c5d","status":"LOST"}
                """, Duration.ZERO);

        assertThatThrownBy(() -> client(baseUrl(), Duration.ofSeconds(2)).dispatch(command))
                .isInstanceOf(DispatchFailedException.class)
                .hasMessageContaining("LOST");
    }

    @Test
    void forwardsTheCorrelationIdOfTheCurrentRequest() throws IOException {
        startServer(200, """
                {"assistanceId":"0b7e2a3c-5d6f-4a1b-8c9d-0e1f2a3b4c5d","status":"NO_CANDIDATE"}
                """, Duration.ZERO);
        MDC.put("correlationId", "demo-mecanicosya");

        client(baseUrl(), Duration.ofSeconds(2)).dispatch(command);

        assertThat(receivedCorrelationId.get()).isEqualTo("demo-mecanicosya");
    }

    @Test
    void anErrorAnswerFromDispatchIsAFailedDispatch() throws IOException {
        startServer(503, "{}", Duration.ZERO);

        assertThatThrownBy(() -> client(baseUrl(), Duration.ofSeconds(2)).dispatch(command))
                .isInstanceOf(DispatchFailedException.class)
                .hasMessageContaining("503");
    }

    @Test
    void noAnswerFromDispatchMeansItIsUnavailable() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> client("http://localhost:" + freePort, Duration.ofSeconds(2)).dispatch(command))
                .isInstanceOf(DispatchUnavailableException.class);
    }

    @Test
    void aSlowDispatchTimesOutAsUnavailable() throws IOException {
        startServer(200, "{}", Duration.ofSeconds(2));

        assertThatThrownBy(() -> client(baseUrl(), Duration.ofMillis(300)).dispatch(command))
                .isInstanceOf(DispatchUnavailableException.class);
    }

    private DispatchRestClient client(String baseUrl, Duration timeout) {
        return new DispatchRestClient(RestClient.builder(), baseUrl, timeout);
    }

    private void startServer(int status, String body, Duration delay) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/dispatches", exchange -> {
            receivedCorrelationId.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }
}
