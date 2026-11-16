package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

public record DispatchCommand(
        UUID assistanceId,
        String requiredSkill,
        double latitude,
        double longitude,
        String vehicleType,
        /** MOBILE, WORKSHOP o null (cualquiera). */
        String mechanicKind
) {
    public DispatchCommand(UUID assistanceId, String requiredSkill, double latitude, double longitude,
                           String vehicleType) {
        this(assistanceId, requiredSkill, latitude, longitude, vehicleType, null);
    }
}

