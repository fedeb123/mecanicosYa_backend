package com.uade.mecanicosya.dispatch.domain;

public class RouteServiceUnavailableException extends RuntimeException {

    public RouteServiceUnavailableException(String message) {
        super(message);
    }

    public RouteServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
