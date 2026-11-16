package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

public class OfferNotFoundException extends RuntimeException {

    public OfferNotFoundException(UUID assistanceId, UUID mechanicId) {
        super("There is no open offer of assistance %s for mechanic %s".formatted(assistanceId, mechanicId));
    }
}
