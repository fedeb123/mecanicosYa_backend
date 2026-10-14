package com.uade.mecanicosya.assistance.domain;

public class InvalidStatusTransitionException extends IllegalStateException {

    public InvalidStatusTransitionException(AssistanceStatus status, String action) {
        super("An assistance in status " + status + " cannot " + action);
    }
}
