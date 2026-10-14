package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceStatus;

import java.time.Instant;
import java.util.UUID;

public record AssistanceStatusChangedEvent(
        UUID assistanceId,
        AssistanceStatus fromStatus,
        AssistanceStatus toStatus,
        Instant occurredAt
) {
    public static AssistanceStatusChangedEvent of(
            UUID assistanceId,
            AssistanceStatus fromStatus,
            AssistanceStatus toStatus
    ) {
        return new AssistanceStatusChangedEvent(assistanceId, fromStatus, toStatus, Instant.now());
    }
}
