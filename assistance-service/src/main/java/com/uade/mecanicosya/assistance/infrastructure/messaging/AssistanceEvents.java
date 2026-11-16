package com.uade.mecanicosya.assistance.infrastructure.messaging;

import com.uade.mecanicosya.shared.messaging.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Eventos que publica assistance-service en {@link Topics#ASSISTANCE_EVENTS}. Llevan sólo lo que necesitan los
 * consumidores: ni el nombre ni el teléfono del repartidor salen del servicio.
 */
public final class AssistanceEvents {

    public static final String REQUESTED = "ASSISTANCE_REQUESTED";
    public static final String CANCELLED = "ASSISTANCE_CANCELLED";
    /** El mecánico terminó el trabajo: dispatch-service lo libera para que reciba ofertas otra vez. */
    public static final String COMPLETED = "ASSISTANCE_COMPLETED";
    public static final String RATED = "ASSISTANCE_RATED";

    private AssistanceEvents() {
    }

    public record Requested(
            UUID eventId,
            String type,
            Instant occurredAt,
            UUID assistanceId,
            String vehicleType,
            String problemType,
            String requiredSkill,
            double latitude,
            double longitude,
            /** MOBILE, WORKSHOP o null (cualquiera): dispatch solo ofrece a mecánicos de ese tipo. */
            String mechanicKind
    ) implements IntegrationEvent {
    }

    public record Cancelled(
            UUID eventId,
            String type,
            Instant occurredAt,
            UUID assistanceId
    ) implements IntegrationEvent {
    }

    public record Completed(
            UUID eventId,
            String type,
            Instant occurredAt,
            UUID assistanceId
    ) implements IntegrationEvent {
    }

    public record Rated(
            UUID eventId,
            String type,
            Instant occurredAt,
            UUID assistanceId,
            UUID mechanicId,
            int score
    ) implements IntegrationEvent {
    }
}
