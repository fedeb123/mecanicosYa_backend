package com.uade.mecanicosya.assistance.application;

import java.util.UUID;

/**
 * Puerto hacia mechanic-service para sumar una calificación al mecánico.
 * La operación es idempotente por asistencia: repetirla no cambia el promedio.
 */
public interface MechanicRatingPort {

    void rate(UUID mechanicId, UUID assistanceId, int score);
}
