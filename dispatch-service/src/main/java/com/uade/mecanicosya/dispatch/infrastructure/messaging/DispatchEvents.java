package com.uade.mecanicosya.dispatch.infrastructure.messaging;

import com.uade.mecanicosya.shared.messaging.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Eventos que publica dispatch-service en {@link Topics#DISPATCH_EVENTS}.
 */
public final class DispatchEvents {

    public static final String OFFERED = "DISPATCH_OFFERED";
    public static final String MATCHED = "DISPATCH_MATCHED";
    public static final String UNMATCHED = "DISPATCH_UNMATCHED";

    private DispatchEvents() {
    }

    public record Offered(
            UUID eventId,
            String type,
            Instant occurredAt,
            UUID assistanceId,
            int round,
            int offers,
            Instant expiresAt
    ) implements IntegrationEvent {
    }

    public record Matched(
            UUID eventId,
            String type,
            Instant occurredAt,
            UUID assistanceId,
            int round,
            UUID mechanicId,
            Double distanceKm,
            Integer etaMinutes
    ) implements IntegrationEvent {
    }

    public record Unmatched(
            UUID eventId,
            String type,
            Instant occurredAt,
            UUID assistanceId,
            int round,
            String reason
    ) implements IntegrationEvent {
    }
}
