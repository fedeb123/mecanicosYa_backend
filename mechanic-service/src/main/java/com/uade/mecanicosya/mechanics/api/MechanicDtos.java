package com.uade.mecanicosya.mechanics.api;

import com.uade.mecanicosya.mechanics.domain.Mechanic;
import com.uade.mecanicosya.mechanics.domain.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public final class MechanicDtos {

    private MechanicDtos() {
    }

    public record CreateMechanicRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @NotEmpty Set<@NotBlank String> skills,
            Set<@NotNull VehicleType> vehicleTypes
    ) {
    }

    public record RatingRequest(
            @NotNull UUID assistanceId,
            @NotNull @Min(1) @Max(5) Integer score
    ) {
    }

    public record AvailabilityRequest(
            boolean available,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude
    ) {
    }

    public record MechanicResponse(
            UUID id,
            String name,
            String email,
            double latitude,
            double longitude,
            boolean available,
            double rating,
            int ratingCount,
            Set<String> skills,
            Set<VehicleType> vehicleTypes
    ) {
        public static MechanicResponse from(Mechanic mechanic) {
            return new MechanicResponse(
                    mechanic.getId(),
                    mechanic.getName(),
                    mechanic.getEmail(),
                    mechanic.getLatitude(),
                    mechanic.getLongitude(),
                    mechanic.isAvailable(),
                    mechanic.getRating(),
                    mechanic.getRatingCount(),
                    mechanic.getSkills(),
                    mechanic.getVehicleTypes()
            );
        }
    }
}

