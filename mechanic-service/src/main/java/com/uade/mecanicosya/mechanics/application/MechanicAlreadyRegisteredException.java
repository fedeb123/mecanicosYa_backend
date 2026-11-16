package com.uade.mecanicosya.mechanics.application;

import java.util.UUID;

public class MechanicAlreadyRegisteredException extends RuntimeException {

    public MechanicAlreadyRegisteredException(UUID id) {
        super("The account " + id + " already has a mechanic profile");
    }
}
