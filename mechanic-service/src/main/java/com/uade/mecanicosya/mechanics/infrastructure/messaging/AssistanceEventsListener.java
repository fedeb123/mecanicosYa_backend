package com.uade.mecanicosya.mechanics.infrastructure.messaging;

import com.uade.mecanicosya.mechanics.application.MechanicFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Suma al promedio del mecánico las calificaciones que publica assistance-service. {@link MechanicFacade#rate} es
 * idempotente por asistencia, así que un mensaje repetido no cuenta dos veces.
 */
@Component
public class AssistanceEventsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssistanceEventsListener.class);

    public static final String TOPIC = "assistance.events";
    static final String RATED = "ASSISTANCE_RATED";

    private final MechanicFacade mechanicFacade;
    private final JsonMapper jsonMapper;

    public AssistanceEventsListener(MechanicFacade mechanicFacade, JsonMapper jsonMapper) {
        this.mechanicFacade = mechanicFacade;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = TOPIC)
    public void onAssistanceEvent(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        if (!RATED.equals(event.path("type").asString(""))) {
            return;
        }
        UUID assistanceId = UUID.fromString(required(event, "assistanceId").asString());
        UUID mechanicId = UUID.fromString(required(event, "mechanicId").asString());
        int score = required(event, "score").asInt();
        LOGGER.info("Received {} for assistance {}: mechanic {} scored {}", RATED, assistanceId, mechanicId, score);
        mechanicFacade.rate(mechanicId, assistanceId, score);
    }

    private static JsonNode required(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Event without " + field + ": " + event);
        }
        return value;
    }
}
