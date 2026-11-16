package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.InvalidStatusTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pasa a {@code UNMATCHED} los pedidos cuya ronda de ofertas venció sin que nadie los aceptara, para que el
 * repartidor sepa que tiene que volver a publicarlos.
 */
@Component
public class OfferExpiryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfferExpiryJob.class);

    private final AssistanceFacade facade;

    public OfferExpiryJob(AssistanceFacade facade) {
        this.facade = facade;
    }

    @Scheduled(fixedDelayString = "${assistance.offers.expiry-check:PT15S}")
    public void expireOffers() {
        for (UUID assistanceId : facade.findWithExpiredOffers()) {
            try {
                facade.markUnmatched(assistanceId);
                LOGGER.info("Nobody accepted assistance {} in time; it is now UNMATCHED", assistanceId);
            } catch (InvalidStatusTransitionException | OptimisticLockingFailureException exception) {
                // Un mecánico la aceptó o el repartidor la canceló mientras tanto.
                LOGGER.debug("Assistance {} changed before it could expire", assistanceId);
            }
        }
    }
}
