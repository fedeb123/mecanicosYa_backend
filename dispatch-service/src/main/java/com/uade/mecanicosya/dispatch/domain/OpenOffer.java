package com.uade.mecanicosya.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido que un mecánico tiene ofrecido y todavía puede aceptar.
 */
public record OpenOffer(
        UUID assistanceId,
        UUID mechanicId,
        String requiredSkill,
        String vehicleType,
        Double latitude,
        Double longitude,
        double distanceKm,
        Integer etaMinutes,
        EtaSource etaSource,
        int ranking,
        Instant expiresAt
) {
}
