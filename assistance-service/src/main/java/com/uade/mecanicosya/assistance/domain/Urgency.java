package com.uade.mecanicosya.assistance.domain;

/**
 * Qué tan urgente es el pedido según el texto del repartidor. HIGH: está varado en un lugar peligroso o el
 * vehículo no es seguro; MEDIUM: no puede seguir trabajando; LOW: puede seguir andando.
 */
public enum Urgency {
    LOW,
    MEDIUM,
    HIGH
}
