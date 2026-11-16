package com.uade.mecanicosya.shared.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento que un servicio publica en Kafka para que lo consuman otros servicios. Cada servicio declara sus propios
 * records: no se comparten clases entre microservicios, sólo el formato JSON.
 */
public interface IntegrationEvent {

    UUID eventId();

    String type();

    Instant occurredAt();
}
