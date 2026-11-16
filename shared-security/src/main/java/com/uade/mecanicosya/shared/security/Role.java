package com.uade.mecanicosya.shared.security;

/**
 * Los roles que viajan en el claim {@code role} del token. {@code SERVICE} es el de las llamadas entre servicios.
 */
public enum Role {
    RIDER,
    MECHANIC,
    ADMIN,
    SERVICE;

    public String authority() {
        return "ROLE_" + name();
    }
}
