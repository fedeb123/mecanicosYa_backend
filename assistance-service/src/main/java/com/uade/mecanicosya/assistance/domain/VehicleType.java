package com.uade.mecanicosya.assistance.domain;

public enum VehicleType {
    BICYCLE,
    E_BIKE,
    MOTORCYCLE;

    public boolean requiresPlate() {
        return this == MOTORCYCLE;
    }
}
