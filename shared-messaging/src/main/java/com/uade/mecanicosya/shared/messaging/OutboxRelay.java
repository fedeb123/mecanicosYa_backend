package com.uade.mecanicosya.shared.messaging;

import com.uade.mecanicosya.shared.observability.CorrelationIdFilter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publica en Kafka los mensajes del outbox, en el orden en que se guardaron. Si Kafka no está, deja de intentar
 * hasta la próxima pasada y conserva el orden. Un mensaje puede salir dos veces si el servicio se cae entre el envío
 * y la marca de publicado: por eso los consumidores son idempotentes.
 */
public class OutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMessageRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration sendTimeout;

    public OutboxRelay(
            OutboxMessageRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock,
            Duration sendTimeout
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.sendTimeout = sendTimeout;
    }

    @Scheduled(fixedDelayString = "${messaging.outbox.relay-interval:PT0.5S}")
    public void relay() {
        List<OutboxMessage> pending = repository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        for (OutboxMessage message : pending) {
            if (!send(message)) {
                return;
            }
            transactionTemplate.executeWithoutResult(status -> repository.markPublished(message.getId(), clock.instant()));
        }
    }

    private boolean send(OutboxMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                message.getTopic(),
                message.getMessageKey(),
                message.getPayload()
        );
        record.headers().add(MessageHeaders.EVENT_ID, bytes(message.getEventId().toString()));
        record.headers().add(MessageHeaders.EVENT_TYPE, bytes(message.getEventType()));
        if (message.getCorrelationId() != null) {
            record.headers().add(MessageHeaders.CORRELATION_ID, bytes(message.getCorrelationId()));
        }
        if (message.getCorrelationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, message.getCorrelationId());
        }
        try {
            kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            LOGGER.info(
                    "Published {} {} to {} (key {})",
                    message.getEventType(),
                    message.getEventId(),
                    message.getTopic(),
                    message.getMessageKey()
            );
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            LOGGER.warn(
                    "Kafka did not take {} {}; it stays in the outbox until the next pass: {}",
                    message.getEventType(),
                    message.getEventId(),
                    exception.getMessage()
            );
            return false;
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
