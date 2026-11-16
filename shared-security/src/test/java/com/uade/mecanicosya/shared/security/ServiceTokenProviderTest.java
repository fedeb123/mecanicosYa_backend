package com.uade.mecanicosya.shared.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTokenProviderTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void asksOnceAndReusesTheTokenUntilItIsAboutToExpire() throws IOException {
        Instant now = Instant.parse("2026-11-03T12:00:00Z");
        startServer(200, now.plus(Duration.ofMinutes(15)));
        MutableClock clock = new MutableClock(now);
        ServiceTokenProvider provider = provider(clock);

        assertThat(provider.token()).isEqualTo("token-1");
        clock.now = now.plus(Duration.ofMinutes(10));
        assertThat(provider.token()).isEqualTo("token-1");
        assertThat(calls).hasValue(1);
        assertThat(lastBody.get()).contains("\"clientId\":\"dispatch-service\"", "\"clientSecret\":\"secret\"");

        clock.now = now.plus(Duration.ofMinutes(14).plusSeconds(30));
        assertThat(provider.token()).isEqualTo("token-2");
    }

    @Test
    void aRejectedSecretLooksLikeTheServiceIsDown() throws IOException {
        startServer(401, Instant.now());

        assertThatThrownBy(() -> provider(Clock.systemUTC()).token())
                .isInstanceOf(ServiceTokenUnavailableException.class)
                .hasMessageContaining("dispatch-service");
    }

    private ServiceTokenProvider provider(Clock clock) {
        return new ServiceTokenProvider("http://localhost:" + server.getAddress().getPort(), "dispatch-service",
                "secret", Duration.ofSeconds(2), clock);
    }

    private void startServer(int status, Instant expiresAt) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/auth/service-token", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"accessToken\":\"token-" + calls.incrementAndGet() + "\",\"expiresAt\":\"" + expiresAt + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
