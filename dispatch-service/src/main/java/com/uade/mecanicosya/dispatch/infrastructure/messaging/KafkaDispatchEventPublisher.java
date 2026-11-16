package com.uade.mecanicosya.dispatch.infrastructure.messaging;

import com.uade.mecanicosya.dispatch.application.DispatchEventPublisher;
import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.shared.messaging.Outbox;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class KafkaDispatchEventPublisher implements DispatchEventPublisher {

    private final Outbox outbox;
    private final Clock clock;

    public KafkaDispatchEventPublisher(Outbox outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void offered(DispatchMatch dispatch, int offers) {
        outbox.publish(Topics.DISPATCH_EVENTS, dispatch.getAssistanceId(), new DispatchEvents.Offered(
                UUID.randomUUID(),
                DispatchEvents.OFFERED,
                clock.instant(),
                dispatch.getAssistanceId(),
                dispatch.getRound(),
                offers,
                dispatch.getOffersExpireAt()
        ));
    }

    @Override
    public void matched(DispatchMatch dispatch) {
        outbox.publish(Topics.DISPATCH_EVENTS, dispatch.getAssistanceId(), new DispatchEvents.Matched(
                UUID.randomUUID(),
                DispatchEvents.MATCHED,
                clock.instant(),
                dispatch.getAssistanceId(),
                dispatch.getRound(),
                dispatch.getMechanicId(),
                dispatch.getDistanceKm(),
                dispatch.getEtaMinutes()
        ));
    }

    @Override
    public void unmatched(DispatchMatch dispatch, UnmatchedReason reason) {
        outbox.publish(Topics.DISPATCH_EVENTS, dispatch.getAssistanceId(), new DispatchEvents.Unmatched(
                UUID.randomUUID(),
                DispatchEvents.UNMATCHED,
                clock.instant(),
                dispatch.getAssistanceId(),
                dispatch.getRound(),
                reason.name()
        ));
    }
}
