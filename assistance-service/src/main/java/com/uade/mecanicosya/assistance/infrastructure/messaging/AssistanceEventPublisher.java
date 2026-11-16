package com.uade.mecanicosya.assistance.infrastructure.messaging;

import com.uade.mecanicosya.assistance.application.AssistanceRequestedEvent;
import com.uade.mecanicosya.assistance.application.AssistanceStatusChangedEvent;
import com.uade.mecanicosya.shared.messaging.Outbox;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Traduce los eventos de dominio a eventos de integración y los deja en el outbox. Escucha dentro de la transacción
 * que creó, canceló o terminó el pedido: si esa transacción se revierte, el evento no sale.
 */
@Component
public class AssistanceEventPublisher {

    private final Outbox outbox;
    private final Clock clock;

    public AssistanceEventPublisher(Outbox outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @EventListener
    public void onRequested(AssistanceRequestedEvent event) {
        outbox.publish(Topics.ASSISTANCE_EVENTS, event.assistanceId(), new AssistanceEvents.Requested(
                event.eventId(),
                AssistanceEvents.REQUESTED,
                clock.instant(),
                event.assistanceId(),
                event.vehicleType() == null ? null : event.vehicleType().name(),
                event.problemType(),
                event.requiredSkill(),
                event.latitude(),
                event.longitude()
        ));
    }

    @EventListener
    public void onStatusChanged(AssistanceStatusChangedEvent event) {
        switch (event.toStatus()) {
            case CANCELLED -> outbox.publish(Topics.ASSISTANCE_EVENTS, event.assistanceId(), new AssistanceEvents.Cancelled(
                    UUID.randomUUID(),
                    AssistanceEvents.CANCELLED,
                    clock.instant(),
                    event.assistanceId()
            ));
            case COMPLETED -> outbox.publish(Topics.ASSISTANCE_EVENTS, event.assistanceId(), new AssistanceEvents.Completed(
                    UUID.randomUUID(),
                    AssistanceEvents.COMPLETED,
                    clock.instant(),
                    event.assistanceId()
            ));
            default -> {
            }
        }
    }
}
