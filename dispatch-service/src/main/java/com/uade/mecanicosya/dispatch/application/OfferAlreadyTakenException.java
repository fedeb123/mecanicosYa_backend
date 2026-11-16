package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

public class OfferAlreadyTakenException extends RuntimeException {

    public OfferAlreadyTakenException(UUID assistanceId) {
        super("Assistance %s was already taken by another mechanic".formatted(assistanceId));
    }
}
