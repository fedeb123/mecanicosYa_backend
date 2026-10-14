package com.uade.mecanicosya.assistance.infrastructure;

import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo JSON que devuelve {@code POST /api/dispatches}.
 */
record DispatchResponse(
        UUID assistanceId,
        UUID mechanicId,
        Double distanceKm,
        String status,
        Instant createdAt
) {
}
