package com.uade.mecanicosya.dispatch.infrastructure.messaging;

import com.uade.mecanicosya.dispatch.application.DispatchCommand;
import com.uade.mecanicosya.dispatch.application.DispatchFacade;
import com.uade.mecanicosya.dispatch.application.DispatchNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Consume los eventos de assistance-service. Es idempotente: si el mismo pedido llega dos veces, {@link
 * DispatchFacade#process} devuelve el despacho que ya existe sin volver a ofrecerlo. Cuando el trabajo termina
 * ({@code ASSISTANCE_COMPLETED}), el mecánico queda libre para recibir ofertas otra vez.
 */
@Component
public class AssistanceEventsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssistanceEventsListener.class);

    static final String REQUESTED = "ASSISTANCE_REQUESTED";
    static final String CANCELLED = "ASSISTANCE_CANCELLED";
    static final String COMPLETED = "ASSISTANCE_COMPLETED";

    private final DispatchFacade dispatchFacade;
    private final JsonMapper jsonMapper;

    public AssistanceEventsListener(DispatchFacade dispatchFacade, JsonMapper jsonMapper) {
        this.dispatchFacade = dispatchFacade;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.ASSISTANCE_EVENTS)
    public void onAssistanceEvent(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        String type = required(event, "type").asString();
        UUID assistanceId = UUID.fromString(required(event, "assistanceId").asString());
        switch (type) {
            case REQUESTED -> {
                LOGGER.info("Received {} for assistance {}", type, assistanceId);
                dispatchFacade.process(new DispatchCommand(
                        assistanceId,
                        required(event, "requiredSkill").asString(),
                        required(event, "latitude").asDouble(),
                        required(event, "longitude").asDouble(),
                        event.path("vehicleType").isString() ? event.get("vehicleType").asString() : null,
                        event.path("mechanicKind").isString() ? event.get("mechanicKind").asString() : null
                ));
            }
            case CANCELLED -> {
                LOGGER.info("Received {} for assistance {}", type, assistanceId);
                try {
                    dispatchFacade.cancel(assistanceId);
                } catch (DispatchNotFoundException exception) {
                    LOGGER.info("Assistance {} was cancelled before it was dispatched", assistanceId);
                }
            }
            case COMPLETED -> {
                LOGGER.info("Received {} for assistance {}", type, assistanceId);
                try {
                    dispatchFacade.complete(assistanceId);
                } catch (DispatchNotFoundException exception) {
                    LOGGER.warn("Assistance {} was completed without a dispatch", assistanceId);
                }
            }
            default -> LOGGER.debug("Ignoring {} for assistance {}", type, assistanceId);
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
