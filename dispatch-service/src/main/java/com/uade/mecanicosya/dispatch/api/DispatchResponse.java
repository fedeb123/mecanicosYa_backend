package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.dispatch.domain.DispatchStatus;

import java.time.Instant;
import java.util.UUID;

public record DispatchResponse(
        UUID assistanceId,
        UUID mechanicId,
        Double distanceKm,
        DispatchStatus status,
        Instant createdAt
) {
    public static DispatchResponse from(DispatchMatch match) {
        return new DispatchResponse(
                match.getAssistanceId(),
                match.getMechanicId(),
                match.getDistanceKm(),
                match.getStatus(),
                match.getCreatedAt()
        );
    }
}
