package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

public record DispatchCommand(
        UUID assistanceId,
        String requiredSkill,
        double latitude,
        double longitude,
        String vehicleType
) {
}

