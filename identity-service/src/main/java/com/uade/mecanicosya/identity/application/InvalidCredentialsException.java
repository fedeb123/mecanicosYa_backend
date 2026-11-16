package com.uade.mecanicosya.identity.application;

/**
 * Email o contraseña incorrectos, refresh token vencido o revocado, o secreto de servicio inválido. El mensaje no
 * dice cuál de los dos datos falló.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
