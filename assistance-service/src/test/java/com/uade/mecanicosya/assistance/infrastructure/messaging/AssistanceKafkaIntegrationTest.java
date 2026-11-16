package com.uade.mecanicosya.assistance.infrastructure.messaging;

import com.uade.mecanicosya.assistance.application.AssistanceCreation;
import com.uade.mecanicosya.assistance.application.AssistanceFacade;
import com.uade.mecanicosya.assistance.application.ClientFacade;
import com.uade.mecanicosya.assistance.application.DispatchPort;
import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.AssistanceStatus;
import com.uade.mecanicosya.assistance.domain.Client;
import com.uade.mecanicosya.assistance.domain.Vehicle;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * La integración con Kafka de verdad: el pedido sale por el outbox con su correlation id, lo que contesta
 * dispatch-service se aplica, y un mensaje roto termina en el dead-letter topic sin trabar a los demás.
 */
@SpringBootTest(properties = {
        "messaging.outbox.relay-interval=PT0.1S",
        "messaging.consumer.retry-interval=PT0.1S"
})
@Testcontainers(disabledWithoutDocker = true)
// Cada clase levanta sus contenedores: el contexto se cierra con ellos para que no queden tareas programadas
// intentando hablar con una base que ya no existe.
@DirtiesContext
class AssistanceKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    @MockitoBean
    DispatchPort dispatchPort;

    @Autowired
    ClientFacade clientFacade;

    @Autowired
    AssistanceFacade assistanceFacade;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    private Client client;
    private Vehicle vehicle;

    @BeforeEach
    void aRiderWithABike() {
        client = clientFacade.register(UUID.randomUUID(), "Rider", "+54 11 5555-0000", UUID.randomUUID() + "@example.com");
        vehicle = clientFacade.registerVehicle(client.getId(), VehicleType.BICYCLE, "Trek", "FX 2", null, null);
    }

    @Test
    void aNewAssistanceIsPublishedWithItsCorrelationId() {
        MDC.put("correlationId", "test-kafka-1");
        AssistanceRequest assistance;
        try {
            assistance = request("kafka-key-1").assistance();
        } finally {
            MDC.remove("correlationId");
        }

        ConsumerRecord<String, String> record = firstRecord(Topics.ASSISTANCE_EVENTS, assistance.getId().toString());

        assertThat(header(record, "eventType")).isEqualTo("ASSISTANCE_REQUESTED");
        assertThat(header(record, "X-Correlation-Id")).isEqualTo("test-kafka-1");
        assertThat(record.value())
                .contains("\"requiredSkill\":\"CHAIN_REPAIR\"")
                .contains("\"vehicleType\":\"BICYCLE\"")
                .doesNotContain("Rider")
                .doesNotContain(client.getEmail());
    }

    @Test
    void theOfferAndTheMatchFromDispatchAreApplied() {
        AssistanceRequest assistance = request("kafka-key-2").assistance();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(2)).truncatedTo(ChronoUnit.MILLIS);
        UUID mechanicId = UUID.randomUUID();

        send(assistance.getId(), """
                {"type":"DISPATCH_OFFERED","assistanceId":"%s","round":1,"offers":2,"expiresAt":"%s"}
                """.formatted(assistance.getId(), expiresAt));
        await().atMost(Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() ->
                assertThat(assistanceFacade.find(assistance.getId()).getOffersExpireAt()).isEqualTo(expiresAt));

        send(assistance.getId(), """
                {"type":"DISPATCH_MATCHED","assistanceId":"%s","round":1,"mechanicId":"%s"}
                """.formatted(assistance.getId(), mechanicId));
        // Un OFFERED que llega tarde, cuando el pedido ya fue tomado, no lo cambia.
        send(assistance.getId(), """
                {"type":"DISPATCH_OFFERED","assistanceId":"%s","round":1,"offers":2,"expiresAt":"%s"}
                """.formatted(assistance.getId(), expiresAt));

        await().atMost(Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() -> {
            AssistanceRequest current = assistanceFacade.find(assistance.getId());
            assertThat(current.getStatus()).isEqualTo(AssistanceStatus.MATCHED);
            assertThat(current.getAssignedMechanicId()).isEqualTo(mechanicId);
        });
    }

    @Test
    void aBrokenMessageGoesToTheDeadLetterTopicAndTheNextOneIsProcessed() {
        AssistanceRequest assistance = request("kafka-key-3").assistance();

        send(assistance.getId(), "{not json");
        send(assistance.getId(), """
                {"type":"DISPATCH_UNMATCHED","assistanceId":"%s","round":1,"reason":"NO_CANDIDATE"}
                """.formatted(assistance.getId()));

        ConsumerRecord<String, String> dead = firstRecord(
                "dispatch.events.assistance-service.dlt",
                assistance.getId().toString()
        );
        assertThat(dead.value()).isEqualTo("{not json");
        await().atMost(Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() ->
                assertThat(assistanceFacade.find(assistance.getId()).getStatus())
                        .isEqualTo(AssistanceStatus.UNMATCHED));
    }

    private AssistanceCreation request(String key) {
        return assistanceFacade.request(
                client.getId(),
                vehicle.getId(),
                "Se cortó la cadena",
                "CHAIN",
                "CHAIN_REPAIR",
                -34.6037,
                -58.3816,
                key + "-" + UUID.randomUUID(),
                null,
                null
        );
    }

    private void send(UUID key, String payload) {
        kafkaTemplate.send(Topics.DISPATCH_EVENTS, key.toString(), payload).join();
    }

    private static ConsumerRecord<String, String> firstRecord(String topic, String key) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ))) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                });
                return !found.isEmpty();
            });
            return found.getFirst();
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
