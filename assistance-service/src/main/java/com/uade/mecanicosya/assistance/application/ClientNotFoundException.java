package com.uade.mecanicosya.assistance.application;

import java.util.UUID;

public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(UUID id) {
        super("Client not found: " + id);
    }
}
