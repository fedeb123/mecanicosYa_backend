package com.uade.mecanicosya.assistance.infrastructure.messaging;

import com.uade.mecanicosya.assistance.application.AssistanceFacade;
import com.uade.mecanicosya.assistance.application.AssistanceNotFoundException;
import com.uade.mecanicosya.assistance.domain.InvalidStatusTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Aplica lo que avisa dispatch-service. Un evento repetido o que llega tarde (por ejemplo, el pedido ya fue tomado
 * o cancelado) no cambia nada: la transición no corresponde y se descarta.
 */
@Component
public class DispatchEventsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchEventsListener.class);

    static final String OFFERED = "DISPATCH_OFFERED";
    static final String MATCHED = "DISPATCH_MATCHED";
    static final String UNMATCHED = "DISPATCH_UNMATCHED";

    private final AssistanceFacade assistanceFacade;
    private final JsonMapper jsonMapper;

    public DispatchEventsListener(AssistanceFacade assistanceFacade, JsonMapper jsonMapper) {
        this.assistanceFacade = assistanceFacade;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.DISPATCH_EVENTS)
    public void onDispatchEvent(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        String type = required(event, "type").asString();
        UUID assistanceId = UUID.fromString(required(event, "assistanceId").asString());
        LOGGER.info("Received {} for assistance {}", type, assistanceId);
        try {
            switch (type) {
                case OFFERED -> assistanceFacade.awaitOffers(
                        assistanceId,
                        Instant.parse(required(event, "expiresAt").asString())
                );
                case MATCHED -> assistanceFacade.assign(
                        assistanceId,
                        UUID.fromString(required(event, "mechanicId").asString())
                );
                case UNMATCHED -> assistanceFacade.markUnmatched(assistanceId);
                default -> LOGGER.debug("Ignoring {} for assistance {}", type, assistanceId);
            }
        } catch (InvalidStatusTransitionException | AssistanceNotFoundException exception) {
            LOGGER.info("Skipping {} for assistance {}: {}", type, assistanceId, exception.getMessage());
        }
    }

    private static JsonNode required(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Event without " + field + ": " + event);
        }
        return value;
    }
}
