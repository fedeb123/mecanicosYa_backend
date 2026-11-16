package com.uade.mecanicosya.dispatch.infrastructure.messaging;

import com.uade.mecanicosya.dispatch.application.AssistanceAssignmentPort;
import com.uade.mecanicosya.dispatch.application.DispatchFacade;
import com.uade.mecanicosya.dispatch.application.MechanicDirectory;
import com.uade.mecanicosya.dispatch.domain.DispatchOfferRepository;
import com.uade.mecanicosya.dispatch.domain.DispatchStatus;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.dispatch.domain.OfferStatus;
import com.uade.mecanicosya.shared.messaging.OutboxMessage;
import com.uade.mecanicosya.shared.messaging.OutboxMessageRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * dispatch-service contra Kafka de verdad: el pedido que llega por el topic se ofrece una sola vez aunque el mensaje
 * se repita, y la cancelación retira las ofertas.
 */
@SpringBootTest(properties = {
        "clients.assistance-service.base-url=http://localhost:1",
        "messaging.outbox.relay-interval=PT0.1S",
        "messaging.consumer.retry-interval=PT0.1S"
})
@Testcontainers(disabledWithoutDocker = true)
// Cada clase levanta sus contenedores: el contexto se cierra con ellos para que no queden tareas programadas
// intentando hablar con una base que ya no existe.
@DirtiesContext
class DispatchKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    @MockitoBean
    MechanicDirectory mechanicDirectory;

    @MockitoBean
    AssistanceAssignmentPort assignmentPort;

    @Autowired
    DispatchFacade facade;

    @Autowired
    DispatchOfferRepository offerRepository;

    @Autowired
    OutboxMessageRepository outbox;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    private final List<MechanicCandidate> mechanics = new ArrayList<>();

    @BeforeEach
    void twoMechanicsNearby() {
        mechanics.clear();
        mechanics.add(mechanic(-34.6040, -58.3820));
        mechanics.add(mechanic(-34.6100, -58.3900));
        when(mechanicDirectory.findCandidates("CHAIN_REPAIR", "BICYCLE")).thenReturn(mechanics);
    }

    @Test
    void aRepeatedRequestIsOfferedOnlyOnceAndKeepsItsCorrelationId() {
        UUID assistanceId = UUID.randomUUID();

        send(assistanceId, requested(assistanceId));
        send(assistanceId, requested(assistanceId));

        ConsumerRecord<String, String> offered = firstRecord(Topics.DISPATCH_EVENTS, assistanceId.toString());
        assertThat(offered.value()).contains("\"type\":\"DISPATCH_OFFERED\"").contains("\"offers\":2");
        assertThat(new String(offered.headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8))
                .isEqualTo("corr-" + assistanceId);
        await().atMost(Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() ->
                assertThat(facade.find(assistanceId).getStatus()).isEqualTo(DispatchStatus.OFFERED));
        // Esperamos a que el segundo mensaje también se procese antes de contar.
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).ignoreExceptions().untilAsserted(() -> {
            assertThat(offerRepository.findByAssistanceIdAndRoundOrderByRankingAsc(assistanceId, 1)).hasSize(2);
            assertThat(eventsOf(assistanceId)).containsExactly("DISPATCH_OFFERED");
        });
    }

    @Test
    void aCancelledAssistanceWithdrawsItsOffers() {
        UUID assistanceId = UUID.randomUUID();

        send(assistanceId, requested(assistanceId));
        send(assistanceId, """
                {"type":"ASSISTANCE_CANCELLED","assistanceId":"%s"}
                """.formatted(assistanceId));

        await().atMost(Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() -> {
            assertThat(facade.find(assistanceId).getStatus()).isEqualTo(DispatchStatus.CANCELLED);
            assertThat(offerRepository.findByAssistanceIdAndRoundOrderByRankingAsc(assistanceId, 1))
                    .extracting(offer -> offer.getStatus())
                    .containsOnly(OfferStatus.WITHDRAWN);
        });
    }

    @Test
    void aCompletedAssistanceFreesTheMechanicForNewOffers() {
        UUID assistanceId = UUID.randomUUID();
        UUID mechanicId = mechanics.getFirst().id();
        send(assistanceId, requested(assistanceId));
        await().atMost(Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() ->
                assertThat(facade.find(assistanceId).getStatus()).isEqualTo(DispatchStatus.OFFERED));
        facade.accept(assistanceId, mechanicId);

        send(assistanceId, """
                {"type":"ASSISTANCE_COMPLETED","assistanceId":"%s"}
                """.formatted(assistanceId));

        await().atMost(Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() ->
                assertThat(facade.find(assistanceId).getCompletedAt()).isNotNull());
        assertThat(facade.find(assistanceId).isActiveJob()).isFalse();
    }

    private List<String> eventsOf(UUID assistanceId) {
        return outbox.findByTopicOrderByIdAsc(Topics.DISPATCH_EVENTS).stream()
                .filter(message -> message.getMessageKey().equals(assistanceId.toString()))
                .map(OutboxMessage::getEventType)
                .toList();
    }

    private static String requested(UUID assistanceId) {
        return """
                {"type":"ASSISTANCE_REQUESTED","assistanceId":"%s","vehicleType":"BICYCLE","problemType":"CHAIN",
                 "requiredSkill":"CHAIN_REPAIR","latitude":-34.6037,"longitude":-58.3816}
                """.formatted(assistanceId);
    }

    private void send(UUID key, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.ASSISTANCE_EVENTS, key.toString(), payload);
        record.headers().add("X-Correlation-Id", ("corr-" + key).getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).join();
    }

    private static MechanicCandidate mechanic(double latitude, double longitude) {
        UUID id = UUID.randomUUID();
        return new MechanicCandidate(id, "Mecánico", id + "@example.com", latitude, longitude, true, 4.5,
                Set.of("CHAIN_REPAIR"));
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
}
