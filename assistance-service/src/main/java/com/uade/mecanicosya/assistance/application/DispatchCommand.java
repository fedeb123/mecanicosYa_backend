package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.VehicleType;

import java.util.UUID;

public record DispatchCommand(
        UUID assistanceId,
        String requiredSkill,
        double latitude,
        double longitude,
        VehicleType vehicleType
) {
}

