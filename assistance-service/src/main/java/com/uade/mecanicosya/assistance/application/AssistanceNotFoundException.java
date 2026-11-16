package com.uade.mecanicosya.assistance.application;

import java.util.UUID;

public class AssistanceNotFoundException extends RuntimeException {

    public AssistanceNotFoundException(UUID id) {
        super("Assistance not found: " + id);
    }

    private AssistanceNotFoundException(String message) {
        super(message);
    }

    public static AssistanceNotFoundException forIdempotencyKey(String idempotencyKey) {
        return new AssistanceNotFoundException("No assistance was created with Idempotency-Key " + idempotencyKey);
    }
}

