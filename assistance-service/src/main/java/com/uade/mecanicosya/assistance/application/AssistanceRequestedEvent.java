package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.VehicleType;

import java.time.Instant;
import java.util.UUID;

public record AssistanceRequestedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID assistanceId,
        UUID clientId,
        UUID vehicleId,
        VehicleType vehicleType,
        String description,
        String problemType,
        double latitude,
        double longitude,
        String requiredSkill
) {
    public static AssistanceRequestedEvent from(AssistanceRequest assistance) {
        return new AssistanceRequestedEvent(
                UUID.randomUUID(),
                "assistance.requested.v2",
                Instant.now(),
                assistance.getId(),
                assistance.getClientId(),
                assistance.getVehicleId(),
                assistance.getVehicleType(),
                assistance.getDescription(),
                assistance.getProblemType(),
                assistance.getLatitude(),
                assistance.getLongitude(),
                assistance.getRequiredSkill()
        );
    }
}
