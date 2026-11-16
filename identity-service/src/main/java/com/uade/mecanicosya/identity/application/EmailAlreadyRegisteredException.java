package com.uade.mecanicosya.identity.application;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("There is already an account for " + email);
    }
}
