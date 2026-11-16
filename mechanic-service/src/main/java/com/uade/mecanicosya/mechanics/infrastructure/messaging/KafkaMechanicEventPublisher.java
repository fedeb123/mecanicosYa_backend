package com.uade.mecanicosya.mechanics.infrastructure.messaging;

import com.uade.mecanicosya.mechanics.application.MechanicEventPublisher;
import com.uade.mecanicosya.mechanics.domain.Mechanic;
import com.uade.mecanicosya.shared.messaging.Outbox;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class KafkaMechanicEventPublisher implements MechanicEventPublisher {

    private final Outbox outbox;
    private final Clock clock;

    public KafkaMechanicEventPublisher(Outbox outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void verificationChanged(Mechanic mechanic) {
        // La clave es el mecánico: sus eventos van a la misma partición y llegan en orden.
        outbox.publish(MechanicEvents.TOPIC, mechanic.getId(), new MechanicEvents.VerificationChanged(
                UUID.randomUUID(),
                MechanicEvents.VERIFICATION_CHANGED,
                clock.instant(),
                mechanic.getId(),
                mechanic.getVerificationStatus().name()
        ));
    }
}
