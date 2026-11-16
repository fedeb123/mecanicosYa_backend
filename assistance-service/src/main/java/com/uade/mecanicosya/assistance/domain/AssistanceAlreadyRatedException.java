package com.uade.mecanicosya.assistance.domain;

import java.util.UUID;

public class AssistanceAlreadyRatedException extends IllegalStateException {

    public AssistanceAlreadyRatedException(UUID assistanceId) {
        super("Assistance " + assistanceId + " was already rated");
    }
}
