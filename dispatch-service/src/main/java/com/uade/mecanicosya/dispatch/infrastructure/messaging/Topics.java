package com.uade.mecanicosya.dispatch.infrastructure.messaging;

/**
 * Topics de Kafka. Cada uno lleva los eventos de un agregado y usa su id como clave, así los eventos de un mismo
 * pedido llegan en orden.
 */
public final class Topics {

    /** Lo publica assistance-service: pedido creado, cancelado o calificado. */
    public static final String ASSISTANCE_EVENTS = "assistance.events";

    /** Lo publica dispatch-service: pedido ofrecido, tomado o sin mecánico. */
    public static final String DISPATCH_EVENTS = "dispatch.events";

    private Topics() {
    }
}
