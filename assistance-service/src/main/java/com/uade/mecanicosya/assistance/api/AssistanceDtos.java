package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.AssistanceStatus;
import com.uade.mecanicosya.assistance.domain.StatusChange;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AssistanceDtos {

    private AssistanceDtos() {
    }

    public record CreateAssistanceRequest(
            @NotNull UUID clientId,
            @NotNull UUID vehicleId,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank String problemType,
            @NotBlank String requiredSkill,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude
    ) {
    }

    public record AssistanceResponse(
            UUID id,
            UUID clientId,
            UUID vehicleId,
            VehicleType vehicleType,
            String description,
            String problemType,
            String requiredSkill,
            double latitude,
            double longitude,
            AssistanceStatus status,
            UUID assignedMechanicId,
            Instant createdAt
    ) {
        public static AssistanceResponse from(AssistanceRequest assistance) {
            return new AssistanceResponse(
                    assistance.getId(),
                    assistance.getClientId(),
                    assistance.getVehicleId(),
                    assistance.getVehicleType(),
                    assistance.getDescription(),
                    assistance.getProblemType(),
                    assistance.getRequiredSkill(),
                    assistance.getLatitude(),
                    assistance.getLongitude(),
                    assistance.getStatus(),
                    assistance.getAssignedMechanicId(),
                    assistance.getCreatedAt()
            );
        }
    }

    public record StatusChangeResponse(
            AssistanceStatus fromStatus,
            AssistanceStatus toStatus,
            Instant changedAt
    ) {
        public static StatusChangeResponse from(StatusChange change) {
            return new StatusChangeResponse(change.getFromStatus(), change.getToStatus(), change.getChangedAt());
        }
    }
}
