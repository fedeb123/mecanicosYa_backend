package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

public class DispatchNotFoundException extends RuntimeException {

    public DispatchNotFoundException(UUID assistanceId) {
        super("Dispatch result not found for assistance: " + assistanceId);
    }
}

