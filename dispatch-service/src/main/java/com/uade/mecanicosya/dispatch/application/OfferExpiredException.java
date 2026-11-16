package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

public class OfferExpiredException extends RuntimeException {

    public OfferExpiredException(UUID assistanceId) {
        super("The offers for assistance %s expired; the rider has to publish it again".formatted(assistanceId));
    }
}
