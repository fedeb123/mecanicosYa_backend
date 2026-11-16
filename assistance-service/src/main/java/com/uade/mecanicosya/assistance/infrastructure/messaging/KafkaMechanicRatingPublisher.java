package com.uade.mecanicosya.assistance.infrastructure.messaging;

import com.uade.mecanicosya.assistance.application.MechanicRatingPort;
import com.uade.mecanicosya.shared.messaging.Outbox;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * La calificación viaja a mechanic-service por Kafka: el repartidor califica aunque mechanic-service esté caído, y
 * el promedio se actualiza cuando vuelve.
 */
@Component
public class KafkaMechanicRatingPublisher implements MechanicRatingPort {

    private final Outbox outbox;
    private final Clock clock;

    public KafkaMechanicRatingPublisher(Outbox outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void rate(UUID mechanicId, UUID assistanceId, int score) {
        outbox.publish(Topics.ASSISTANCE_EVENTS, assistanceId, new AssistanceEvents.Rated(
                UUID.randomUUID(),
                AssistanceEvents.RATED,
                clock.instant(),
                assistanceId,
                mechanicId,
                score
        ));
    }
}
