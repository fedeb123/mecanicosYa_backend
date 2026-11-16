package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.InvalidStatusTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
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

    /**
     * Publica el pedido (o lo vuelve a publicar si quedó sin mecánico) y aplica lo que contesta dispatch-service.
     */
    public AssistanceRequest dispatch(UUID assistanceId) {
        AssistanceRequest assistance = assistanceFacade.find(assistanceId);
        switch (assistance.getStatus()) {
            case UNMATCHED -> assistance = assistanceFacade.republish(assistanceId);
            case REQUESTED -> {
            }
            default -> throw new InvalidStatusTransitionException(assistance.getStatus(), "be dispatched");
        }
        DispatchOutcome outcome = dispatchPort.dispatch(new DispatchCommand(
                assistance.getId(),
                assistance.getRequiredSkill(),
                assistance.getLatitude(),
                assistance.getLongitude(),
                assistance.getVehicleType(),
                assistance.getPreferredMechanicKind()
        ));
        switch (outcome) {
            case DispatchOutcome.Offered(Instant expiresAt, int offers) -> {
                assistanceFacade.awaitOffers(assistanceId, expiresAt);
                LOGGER.info("Assistance {} offered to {} mechanic(s) until {}", assistanceId, offers, expiresAt);
            }
            case DispatchOutcome.Matched(UUID mechanicId, double distanceKm) -> {
                assistanceFacade.applyMatch(assistanceId, mechanicId);
                LOGGER.info(
                        "Assistance {} matched with mechanic {} at {} km",
                        assistanceId,
                        mechanicId,
                        distanceKm
                );
            }
            case DispatchOutcome.NoCandidate() -> {
                assistanceFacade.markUnmatched(assistanceId);
                LOGGER.info("No mechanic available for assistance {}; it is UNMATCHED", assistanceId);
            }
        }
        return assistanceFacade.find(assistanceId);
    }
}
