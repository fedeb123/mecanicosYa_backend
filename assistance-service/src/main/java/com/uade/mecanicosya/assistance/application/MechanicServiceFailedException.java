package com.uade.mecanicosya.assistance.application;

public class MechanicServiceFailedException extends RuntimeException {

    public MechanicServiceFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
