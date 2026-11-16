package com.uade.mecanicosya.dispatch.application;

import java.time.Duration;

/**
 * Cuántos mecánicos reciben cada pedido y cuánto dura una ronda de ofertas.
 */
public record OfferPolicy(int maxOffers, Duration ttl) {

    public OfferPolicy {
        if (maxOffers < 1) {
            throw new IllegalArgumentException("dispatch.offers.max-offers must be at least 1");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("dispatch.offers.ttl must be positive");
        }
    }
}
