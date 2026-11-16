package com.uade.mecanicosya.dispatch.infrastructure;

import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.RoutePlan;
import com.uade.mecanicosya.dispatch.domain.RouteServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba el cliente contra un servidor HTTP local que responde como la Directions API de OpenRouteService. */
class OpenRouteServiceDirectionsTest {

    private static final String ROUTE = """
            {"type":"FeatureCollection","features":[{"geometry":{"type":"LineString","coordinates":
            [[-58.3830,-34.6045],[-58.3830,-34.6037],[-58.3816,-34.6037]]},
            "properties":{"summary":{"distance":430.4,"duration":150.0},"segments":[{"steps":[
            {"distance":90.0,"duration":30.0,"type":11,"instruction":"Dirigite al norte por Sarmiento","name":"Sarmiento","way_points":[0,1]},
            {"distance":340.4,"duration":120.0,"type":1,"instruction":"Girá a la derecha por Corrientes","name":"Corrientes","way_points":[1,2]},
            {"distance":0.0,"duration":0.0,"type":10,"instruction":"Llegaste a tu destino","name":"-","way_points":[2,2]}]}]}}]}
            """;

    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = ROUTE;
    private HttpServer server;
    private OpenRouteServiceDirections client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/geo+json;charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new OpenRouteServiceDirections(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "cycling-regular", Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsTheLineAndTheStepsInSpanish() {
        RoutePlan plan = client.plan(-34.6045, -58.3830, -34.6037, -58.3816);

        assertThat(path.get()).isEqualTo("/v2/directions/cycling-regular/geojson");
        assertThat(requestBody.get()).contains("\"language\":\"es\"").contains("[-58.383,-34.6045]");
        assertThat(plan.source()).isEqualTo(EtaSource.ROUTE);
        assertThat(plan.distanceKm()).isEqualTo(0.43);
        assertThat(plan.durationMinutes()).isEqualTo(3);
        assertThat(plan.points()).hasSize(3);
        assertThat(plan.points().getFirst()).containsExactly(-34.6045, -58.3830);
        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps().get(1).instruction()).isEqualTo("Girá a la derecha por Corrientes");
        assertThat(plan.steps().get(1).latitude()).isEqualTo(-34.6037);
        assertThat(plan.steps().get(1).longitude()).isEqualTo(-58.3830);
        assertThat(plan.steps().get(2).street()).isNull();
    }

    @Test
    void anErrorFromTheApiIsReportedAsUnavailable() {
        status = 404;
        responseBody = "{\"error\":{\"code\":2010,\"message\":\"Could not find routable point\"}}";

        assertThatThrownBy(() -> client.plan(-34.6045, -58.3830, -34.6037, -58.3816))
                .isInstanceOf(RouteServiceUnavailableException.class);
    }
}
