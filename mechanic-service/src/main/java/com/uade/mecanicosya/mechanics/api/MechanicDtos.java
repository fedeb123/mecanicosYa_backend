package com.uade.mecanicosya.mechanics.api;

import com.uade.mecanicosya.mechanics.application.MechanicFacade;
import com.uade.mecanicosya.mechanics.domain.Mechanic;
import com.uade.mecanicosya.mechanics.domain.VehicleType;
import com.uade.mecanicosya.mechanics.domain.VerificationStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

    /** El id y el email salen del token del mecánico. */
    public record CreateMechanicRequest(
            @NotBlank String name,
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
            Set<VehicleType> vehicleTypes,
            VerificationStatus verificationStatus
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
                    mechanic.getVehicleTypes(),
                    mechanic.getVerificationStatus()
            );
        }
    }

    /** Lo que ve el repartidor en el mapa: sin email ni datos de contacto. */
    public record NearbyMechanicResponse(
            UUID id,
            String name,
            double latitude,
            double longitude,
            double distanceKm,
            double rating,
            Set<VehicleType> vehicleTypes
    ) {
        public static NearbyMechanicResponse from(MechanicFacade.NearbyMechanic nearby) {
            Mechanic mechanic = nearby.mechanic();
            return new NearbyMechanicResponse(
                    mechanic.getId(),
                    mechanic.getName(),
                    mechanic.getLatitude(),
                    mechanic.getLongitude(),
                    Math.round(nearby.distanceKm() * 100) / 100.0,
                    mechanic.getRating(),
                    mechanic.getVehicleTypes()
            );
        }
    }
}
