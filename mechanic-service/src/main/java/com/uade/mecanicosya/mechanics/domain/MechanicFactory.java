package com.uade.mecanicosya.mechanics.domain;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class MechanicFactory {

    /** Crea el perfil con el id de la cuenta en identity-service, que es el {@code sub} del token. */
    public Mechanic create(
            UUID id,
            String name,
            String email,
            double latitude,
            double longitude,
            Set<String> skills,
            Set<VehicleType> vehicleTypes
    ) {
        return new Mechanic(
                id,
                name.trim(),
                email.trim().toLowerCase(),
                latitude,
                longitude,
                skills,
                vehicleTypes
        );
    }
}

