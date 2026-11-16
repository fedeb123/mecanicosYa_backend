package com.uade.mecanicosya.mechanics.infrastructure.messaging;

import com.uade.mecanicosya.shared.messaging.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Eventos que publica mechanic-service en {@link #TOPIC}. identity-service los usa para que el próximo token del
 * mecánico traiga su estado de validación.
 */
public final class MechanicEvents {

    public static final String TOPIC = "mechanic.events";
    public static final String VERIFICATION_CHANGED = "MECHANIC_VERIFICATION_CHANGED";

    private MechanicEvents() {
    }

    public record VerificationChanged(
            UUID eventId,
            String type,
            Instant occurredAt,
            UUID mechanicId,
            String verificationStatus
    ) implements IntegrationEvent {
    }
}
