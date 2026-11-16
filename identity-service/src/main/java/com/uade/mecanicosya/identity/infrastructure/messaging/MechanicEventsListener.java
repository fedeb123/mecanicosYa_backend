package com.uade.mecanicosya.identity.infrastructure.messaging;

import com.uade.mecanicosya.identity.application.AuthFacade;
import com.uade.mecanicosya.identity.domain.VerificationStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Copia el estado de validación que publica mechanic-service, para que el próximo token del mecánico lo traiga.
 */
@Component
public class MechanicEventsListener {

    public static final String TOPIC = "mechanic.events";
    static final String VERIFICATION_CHANGED = "MECHANIC_VERIFICATION_CHANGED";

    private final AuthFacade authFacade;
    private final JsonMapper jsonMapper;

    public MechanicEventsListener(AuthFacade authFacade, JsonMapper jsonMapper) {
        this.authFacade = authFacade;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = TOPIC)
    public void onMechanicEvent(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        if (!VERIFICATION_CHANGED.equals(event.path("type").asString(""))) {
            return;
        }
        UUID mechanicId = UUID.fromString(required(event, "mechanicId"));
        VerificationStatus status = VerificationStatus.valueOf(required(event, "verificationStatus"));
        authFacade.changeVerification(mechanicId, status);
    }

    private static String required(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Event without " + field + ": " + event);
        }
        return value.asString();
    }
}
