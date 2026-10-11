package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class AssistanceDispatchCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssistanceDispatchCoordinator.class);

    private final AssistanceFacade assistanceFacade;
    private final DispatchPort dispatchPort;

    public AssistanceDispatchCoordinator(AssistanceFacade assistanceFacade, DispatchPort dispatchPort) {
        this.assistanceFacade = assistanceFacade;
        this.dispatchPort = dispatchPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssistanceRequested(AssistanceRequestedEvent event) {
        LOGGER.info(
                "Received {} for assistance {}, calling dispatch-service",
                event.eventType(),
                event.assistanceId()
        );
        try {
            dispatch(event.assistanceId());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Remote dispatch failed for assistance {}. It can be retried through the REST endpoint.",
                    event.assistanceId(),
                    exception
            );
        }
    }

    public AssistanceRequest dispatch(UUID assistanceId) {
        AssistanceRequest assistance = assistanceFacade.find(assistanceId);
        DispatchOutcome outcome = dispatchPort.dispatch(new DispatchCommand(
                assistance.getId(),
                assistance.getRequiredSkill(),
                assistance.getLatitude(),
                assistance.getLongitude(),
                assistance.getVehicleType()
        ));
        switch (outcome) {
            case DispatchOutcome.Matched(UUID mechanicId, double distanceKm) -> {
                assistanceFacade.applyMatch(assistanceId, mechanicId);
                LOGGER.info(
                        "Assistance {} matched with mechanic {} at {} km",
                        assistanceId,
                        mechanicId,
                        distanceKm
                );
            }
            case DispatchOutcome.NoCandidate() -> LOGGER.info(
                    "No mechanic available for assistance {}; it stays REQUESTED",
                    assistanceId
            );
        }
        return assistanceFacade.find(assistanceId);
    }
}
