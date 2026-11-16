package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.AssistanceStatus;
import com.uade.mecanicosya.assistance.domain.StatusChange;
import com.uade.mecanicosya.assistance.domain.TriageSource;
import com.uade.mecanicosya.assistance.domain.Urgency;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AssistanceDtos {

    private AssistanceDtos() {
    }

    /** {@code clientId} es opcional: sale del token. Si viene, tiene que ser el del token. */
    public record CreateAssistanceRequest(
            UUID clientId,
            @NotNull UUID vehicleId,
            @NotBlank @Size(max = 1000) String description,
            @Size(max = 80) String problemType,
            @Size(max = 80) String requiredSkill,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            Instant clientCreatedAt
    ) {
    }

    public record AssignMechanicRequest(@NotNull UUID mechanicId) {
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
            Integer assignedEtaMinutes,
            Double assignedDistanceKm,
            Integer rating,
            String ratingComment,
            Instant offersExpireAt,
            String idempotencyKey,
            Instant clientCreatedAt,
            Instant createdAt,
            Urgency urgency,
            TriageSource triageSource,
            Double triageConfidence
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
                    assistance.getAssignedEtaMinutes(),
                    assistance.getAssignedDistanceKm(),
                    assistance.getRating(),
                    assistance.getRatingComment(),
                    assistance.getOffersExpireAt(),
                    assistance.getIdempotencyKey(),
                    assistance.getClientCreatedAt(),
                    assistance.getCreatedAt(),
                    assistance.getUrgency(),
                    assistance.getTriageSource(),
                    assistance.getTriageConfidence()
            );
        }
    }

    public record RateAssistanceRequest(
            @NotNull @Min(1) @Max(5) Integer score,
            @Size(max = 500) String comment
    ) {
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
