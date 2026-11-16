package com.uade.mecanicosya.mechanics.domain;

import java.util.UUID;

public class MechanicNotApprovedException extends RuntimeException {

    public MechanicNotApprovedException(UUID mechanicId) {
        super("Mechanic " + mechanicId + " is pending verification: an admin must approve them first");
    }
}
