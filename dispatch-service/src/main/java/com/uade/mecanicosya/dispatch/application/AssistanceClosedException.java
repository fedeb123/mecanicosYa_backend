package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

public class AssistanceClosedException extends RuntimeException {

    public AssistanceClosedException(UUID assistanceId) {
        super("Assistance %s is no longer waiting for a mechanic".formatted(assistanceId));
    }
}
