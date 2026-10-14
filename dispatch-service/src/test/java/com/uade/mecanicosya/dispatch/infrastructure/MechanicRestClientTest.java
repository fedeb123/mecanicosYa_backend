package com.uade.mecanicosya.dispatch.infrastructure;

import com.uade.mecanicosya.dispatch.application.MechanicDirectoryUnavailableException;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MechanicRestClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsSkillAndVehicleTypeAndReadsTheCandidates() throws IOException {
        AtomicReference<String> query = new AtomicReference<>();
        String body = """
                [{"id":"6f1c8f4e-1b7a-4c1e-9b1a-2a8c9d0e1f23","name":"Ada","email":"ada@example.com",
                  "latitude":-34.6,"longitude":-58.38,"available":true,"rating":4.8,"skills":["CHAIN_REPAIR"]}]
                """;
        startServer(200, body, query);

        List<MechanicCandidate> candidates = client(baseUrl()).findCandidates("CHAIN_REPAIR", "E_BIKE");

        assertThat(query.get()).contains("skill=CHAIN_REPAIR").contains("vehicleType=E_BIKE");
        assertThat(candidates).extracting(MechanicCandidate::name).containsExactly("Ada");
    }

    @Test
    void reportsTheDirectoryAsUnavailableWhenMechanicServiceFails() throws IOException {
        startServer(500, "{}", new AtomicReference<>());

        assertThatThrownBy(() -> client(baseUrl()).findCandidates("CHAIN_REPAIR", null))
                .isInstanceOf(MechanicDirectoryUnavailableException.class);
    }

    @Test
    void reportsTheDirectoryAsUnavailableWhenNothingIsListening() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> client("http://localhost:" + freePort).findCandidates("CHAIN_REPAIR", null))
                .isInstanceOf(MechanicDirectoryUnavailableException.class);
    }

    @Test
    void reportsTheDirectoryAsUnavailableWhenMechanicServiceIsTooSlow() throws IOException {
        startServer(200, "[]", new AtomicReference<>(), Duration.ofSeconds(2));

        assertThatThrownBy(() -> client(baseUrl(), Duration.ofMillis(300)).findCandidates("CHAIN_REPAIR", null))
                .isInstanceOf(MechanicDirectoryUnavailableException.class);
    }

    private MechanicRestClient client(String baseUrl) {
        return client(baseUrl, Duration.ofSeconds(2));
    }

    private MechanicRestClient client(String baseUrl, Duration timeout) {
        return new MechanicRestClient(RestClient.builder(), baseUrl, timeout);
    }

    private void startServer(int status, String body, AtomicReference<String> query) throws IOException {
        startServer(status, body, query, Duration.ZERO);
    }

    private void startServer(int status, String body, AtomicReference<String> query, Duration delay) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/mechanics/candidates", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
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
