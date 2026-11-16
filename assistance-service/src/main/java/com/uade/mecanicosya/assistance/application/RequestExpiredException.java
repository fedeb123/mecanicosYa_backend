package com.uade.mecanicosya.assistance.application;

import java.time.Duration;

/**
 * El pedido estuvo en cola en el teléfono más tiempo del que tiene sentido: se rechaza para que la app le avise al
 * repartidor que la ayuda nunca se pidió.
 */
public class RequestExpiredException extends RuntimeException {

    public RequestExpiredException(Duration age, Duration maxAge) {
        super("The request was created %d minute(s) ago on the device; requests older than %d minute(s) are rejected"
                .formatted(age.toMinutes(), maxAge.toMinutes()));
    }
}
