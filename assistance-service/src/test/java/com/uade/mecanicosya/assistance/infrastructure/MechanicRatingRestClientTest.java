package com.uade.mecanicosya.assistance.infrastructure;

import com.uade.mecanicosya.assistance.application.MechanicServiceFailedException;
import com.uade.mecanicosya.assistance.application.MechanicServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

class MechanicRatingRestClientTest {

    private final UUID mechanicId = UUID.fromString("6f1c8f4e-1b7a-4c1e-9b1a-2a8c9d0e1f23");
    private final UUID assistanceId = UUID.fromString("0b7e2a3c-5d6f-4a1b-8c9d-0e1f2a3b4c5d");
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsTheScoreForTheAssistanceToTheMechanic() throws IOException {
        startServer(200);

        client("http://localhost:" + server.getAddress().getPort()).rate(mechanicId, assistanceId, 4);

        assertThat(receivedPath.get()).isEqualTo("/api/mechanics/" + mechanicId + "/ratings");
        assertThat(receivedBody.get())
                .contains("\"assistanceId\":\"" + assistanceId + "\"")
                .contains("\"score\":4");
    }

    @Test
    void anErrorAnswerIsAFailedRating() throws IOException {
        startServer(404);

        assertThatThrownBy(() -> client("http://localhost:" + server.getAddress().getPort())
                .rate(mechanicId, assistanceId, 4))
                .isInstanceOf(MechanicServiceFailedException.class)
                .hasMessageContaining("404");
    }

    @Test
    void noAnswerMeansMechanicServiceIsUnavailable() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> client("http://localhost:" + freePort).rate(mechanicId, assistanceId, 4))
                .isInstanceOf(MechanicServiceUnavailableException.class);
    }

    private MechanicRatingRestClient client(String baseUrl) {
        return new MechanicRatingRestClient(RestClient.builder(), baseUrl, Duration.ofSeconds(2));
    }

    private void startServer(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/mechanics/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
