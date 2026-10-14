package com.uade.mecanicosya.mechanics.application;

import java.util.UUID;

public class MechanicNotFoundException extends RuntimeException {

    public MechanicNotFoundException(UUID id) {
        super("Mechanic not found: " + id);
    }
}

