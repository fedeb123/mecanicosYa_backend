package com.uade.mecanicosya.assistance.application;

import java.util.UUID;

public class AssistanceNotFoundException extends RuntimeException {

    public AssistanceNotFoundException(UUID id) {
        super("Assistance not found: " + id);
    }
}

