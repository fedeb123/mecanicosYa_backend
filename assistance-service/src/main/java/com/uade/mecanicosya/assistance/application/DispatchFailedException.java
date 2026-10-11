package com.uade.mecanicosya.assistance.application;

public class DispatchFailedException extends RuntimeException {

    public DispatchFailedException(String message) {
        super(message);
    }

    public DispatchFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
