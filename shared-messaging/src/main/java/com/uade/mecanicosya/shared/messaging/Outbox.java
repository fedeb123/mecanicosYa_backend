package com.uade.mecanicosya.shared.messaging;

import com.uade.mecanicosya.shared.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Guarda un evento para publicarlo en Kafka después del commit (patrón Transactional Outbox). Exige una transacción
 * abierta: el evento se confirma o se descarta junto con el cambio que lo originó.
 */
public class Outbox {

    private final OutboxMessageRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public Outbox(OutboxMessageRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, Object key, IntegrationEvent event) {
        repository.save(new OutboxMessage(
                event.eventId(),
                topic,
                String.valueOf(key),
                event.type(),
                jsonMapper.writeValueAsString(event),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                clock.instant()
        ));
    }
}
