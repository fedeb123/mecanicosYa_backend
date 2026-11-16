package com.uade.mecanicosya.mechanics.api;

import com.uade.mecanicosya.mechanics.application.MechanicFacade;
import com.uade.mecanicosya.mechanics.domain.Mechanic;
import com.uade.mecanicosya.mechanics.domain.MechanicKind;
import com.uade.mecanicosya.mechanics.domain.VehicleType;
import com.uade.mecanicosya.mechanics.domain.VerificationStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public final class MechanicDtos {

    private MechanicDtos() {
    }

    /** El id y el email salen del token del mecánico. */
    public record CreateMechanicRequest(
            @NotBlank String name,
            @Size(max = 40) String phone,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @NotEmpty Set<@NotBlank String> skills,
            Set<@NotNull VehicleType> vehicleTypes,
            /** MOBILE (por defecto) o WORKSHOP; un taller manda su dirección y la ubicación de esa dirección. */
            MechanicKind kind,
            @Size(max = 200) String address
    ) {
        /** Un taller necesita dirección y teléfono: el repartidor tiene que saber adónde ir y poder llamar. */
        @AssertTrue(message = "A workshop needs an address and a phone")
        public boolean isWorkshopComplete() {
            return kind != MechanicKind.WORKSHOP
                    || (address != null && !address.isBlank() && phone != null && !phone.isBlank());
        }
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
            /** Para que el repartidor asignado lo llame; null si no lo cargó. */
            String phone,
            MechanicKind kind,
            /** Dirección del taller; null para un mecánico móvil. */
            String address,
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
                    mechanic.getPhone(),
                    mechanic.getKind(),
                    mechanic.getAddress(),
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
            MechanicKind kind,
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
                    mechanic.getKind(),
                    mechanic.getLatitude(),
                    mechanic.getLongitude(),
                    Math.round(nearby.distanceKm() * 100) / 100.0,
                    mechanic.getRating(),
                    mechanic.getVehicleTypes()
            );
        }
    }
}
