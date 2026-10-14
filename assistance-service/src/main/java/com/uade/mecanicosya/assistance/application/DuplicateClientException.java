package com.uade.mecanicosya.assistance.application;

public class DuplicateClientException extends RuntimeException {

    public DuplicateClientException(String email) {
        super("A client with email " + email + " already exists");
    }
}
