package com.uade.mecanicosya.shared.security;

import org.springframework.web.client.ResourceAccessException;

/**
 * identity-service no dio un token de servicio. Extiende {@link ResourceAccessException} para que cada cliente REST
 * lo trate igual que una caída del servicio al que iba a llamar.
 */
public class ServiceTokenUnavailableException extends ResourceAccessException {

    public ServiceTokenUnavailableException(String message, Throwable cause) {
        super(message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
