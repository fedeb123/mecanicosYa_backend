package com.uade.mecanicosya.assistance.application.triage;

public class TriageUnavailableException extends RuntimeException {

    public TriageUnavailableException(String message) {
        super(message);
    }

    public TriageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
