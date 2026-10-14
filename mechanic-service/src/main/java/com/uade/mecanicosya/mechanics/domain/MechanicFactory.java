package com.uade.mecanicosya.mechanics.domain;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class MechanicFactory {

    public Mechanic create(
            String name,
            String email,
            double latitude,
            double longitude,
            Set<String> skills,
            Set<VehicleType> vehicleTypes
    ) {
        return new Mechanic(
                UUID.randomUUID(),
                name.trim(),
                email.trim().toLowerCase(),
                latitude,
                longitude,
                skills,
                vehicleTypes
        );
    }
}

