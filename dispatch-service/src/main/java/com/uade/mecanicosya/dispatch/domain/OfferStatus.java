package com.uade.mecanicosya.dispatch.domain;

public enum OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    /** Otro mecánico aceptó primero. */
    LOST,
    EXPIRED,
    /** El pedido se canceló antes de que el mecánico respondiera. */
    WITHDRAWN
}
