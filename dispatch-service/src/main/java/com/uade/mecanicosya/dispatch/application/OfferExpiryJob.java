package com.uade.mecanicosya.dispatch.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cierra las rondas de ofertas vencidas. Aunque no corra, nadie puede aceptar una oferta vencida: la aceptación
 * compara contra la hora de vencimiento. Esto sólo deja el estado al día para las consultas.
 */
@Component
public class OfferExpiryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfferExpiryJob.class);

    private final DispatchFacade facade;

    public OfferExpiryJob(DispatchFacade facade) {
        this.facade = facade;
    }

    @Scheduled(fixedDelayString = "${dispatch.offers.expiry-check:PT15S}")
    public void expireOffers() {
        try {
            int expired = facade.expireOffers();
            if (expired > 0) {
                LOGGER.info("{} offer round(s) expired without a mechanic", expired);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not expire offers; it will be retried on the next run", exception);
        }
    }
}
