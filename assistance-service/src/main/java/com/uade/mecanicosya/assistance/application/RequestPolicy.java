package com.uade.mecanicosya.assistance.application;

import java.time.Duration;

/**
 * Cuánto puede tardar en llegar un pedido creado sin conexión, y cuánto adelantado puede venir el reloj del teléfono.
 */
public record RequestPolicy(Duration maxAge, Duration maxClockSkew) {
}
