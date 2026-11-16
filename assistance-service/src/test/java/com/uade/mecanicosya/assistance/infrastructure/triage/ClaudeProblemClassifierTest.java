package com.uade.mecanicosya.assistance.infrastructure.triage;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.OutputConfig;
import com.uade.mecanicosya.assistance.application.triage.ProblemCategory;
import com.uade.mecanicosya.assistance.application.triage.Triage;
import com.uade.mecanicosya.assistance.application.triage.TriageUnavailableException;
import com.uade.mecanicosya.assistance.domain.TriageSource;
import com.uade.mecanicosya.assistance.domain.Urgency;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba el cliente contra un servidor HTTP local que responde como la API de Claude. */
class ClaudeProblemClassifierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> betaHeader = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody;
    private ClaudeProblemClassifier classifier;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            betaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        classifier = new ClaudeProblemClassifier(
                AnthropicOkHttpClient.builder()
                        .apiKey("test-key")
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .timeout(Duration.ofSeconds(5))
                        .maxRetries(0)
                        .build(),
                "claude-opus-5-5",
                OutputConfig.Effort.LOW,
                2048,
                true
        );
    }

    @AfterEach
    void stopServer() {
        classifier.close();
        server.stop(0);
    }

    @Test
    void readsTheStructuredAnswer() {
        responseBody = message("end_turn", "{\"category\":\"WHEEL\",\"urgency\":\"HIGH\",\"confidence\":0.86}");

        Triage triage = classifier.classify("me chocó un auto y la rueda quedó doblada", VehicleType.BICYCLE);

        assertThat(triage).isEqualTo(new Triage(ProblemCategory.WHEEL, Urgency.HIGH, 0.86, TriageSource.AI));
        assertThat(triage.requiredSkill()).isEqualTo("WHEEL_REPAIR");
    }

    @Test
    void sendsOnlyTheDescriptionAndTheVehicleTypeWithASchema() {
        responseBody = message("end_turn", "{\"category\":\"CHAIN\",\"urgency\":\"MEDIUM\",\"confidence\":0.9}");

        classifier.classify("Se me cortó la cadena", VehicleType.E_BIKE);

        JsonNode sent = JSON.readTree(requestBody.get());
        assertThat(sent.get("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(sent.get("output_config").get("effort").asString()).isEqualTo("low");
        assertThat(sent.get("output_config").get("format").get("type").asString()).isEqualTo("json_schema");
        assertThat(sent.get("output_config").get("format").get("schema").toString())
                .contains("WHEEL", "ELECTRICAL", "HIGH");
        assertThat(sent.get("fallbacks").asString()).isEqualTo("default");
        assertThat(betaHeader.get()).contains("server-side-fallback-2026-07-01");
        assertThat(sent.get("messages")).hasSize(1);
        assertThat(sent.get("messages").get(0).toString())
                .contains("Se me cortó la cadena", "E_BIKE");
    }

    @Test
    void aRefusalIsReportedAsUnavailable() {
        responseBody = message("refusal", null);

        assertThatThrownBy(() -> classifier.classify("texto", VehicleType.BICYCLE))
                .isInstanceOf(TriageUnavailableException.class);
    }

    @Test
    void anApiErrorIsReportedAsUnavailable() {
        status = 529;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}";

        assertThatThrownBy(() -> classifier.classify("se me cortó la cadena", VehicleType.BICYCLE))
                .isInstanceOf(TriageUnavailableException.class);
    }

    @Test
    void anAnswerOutsideTheSchemaIsReportedAsUnavailable() {
        responseBody = message("end_turn", "{\"category\":\"SPACESHIP\",\"urgency\":\"HIGH\",\"confidence\":1}");

        assertThatThrownBy(() -> classifier.classify("se me cortó la cadena", VehicleType.BICYCLE))
                .isInstanceOf(TriageUnavailableException.class);
    }

    private static String message(String stopReason, String text) {
        String content = text == null ? "[]"
                : "[{\"type\":\"text\",\"text\":" + JSON.writeValueAsString(text) + "}]";
        return """
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":%s,"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":120,"output_tokens":30}}""".formatted(content, stopReason);
    }
}
