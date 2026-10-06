package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.domain.Client;
import com.uade.mecanicosya.assistance.domain.Vehicle;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ClientDtos {

    private ClientDtos() {
    }

    public record CreateClientRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Email @Size(max = 120) String email
    ) {
    }

    public record ClientResponse(
            UUID id,
            String fullName,
            String phone,
            String email,
            Instant createdAt
    ) {
        public static ClientResponse from(Client client) {
            return new ClientResponse(
                    client.getId(),
                    client.getFullName(),
                    client.getPhone(),
                    client.getEmail(),
                    client.getCreatedAt()
            );
        }
    }

    public record CreateVehicleRequest(
            @NotNull VehicleType type,
            @NotBlank @Size(max = 60) String brand,
            @NotBlank @Size(max = 60) String model,
            @Size(max = 30) String color,
            @Size(max = 10) String plate
    ) {
    }

    public record VehicleResponse(
            UUID id,
            UUID clientId,
            VehicleType type,
            String brand,
            String model,
            String color,
            String plate
    ) {
        public static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(
                    vehicle.getId(),
                    vehicle.getClient().getId(),
                    vehicle.getType(),
                    vehicle.getBrand(),
                    vehicle.getModel(),
                    vehicle.getColor(),
                    vehicle.getPlate()
            );
        }
    }
}
