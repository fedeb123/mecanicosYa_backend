package com.uade.mecanicosya.dispatch.domain;

public enum DispatchStatus {
    /** El pedido se ofreció a los candidatos y espera que alguno lo acepte. */
    OFFERED,
    /** Un mecánico aceptó la oferta. */
    MATCHED,
    /** No había ningún candidato al momento del pedido. */
    NO_CANDIDATE,
    /** La ronda de ofertas venció sin que nadie la aceptara. */
    UNMATCHED,
    /** El repartidor canceló el pedido. */
    CANCELLED
}
