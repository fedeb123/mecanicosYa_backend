package com.uade.mecanicosya.assistance.application;

import java.util.UUID;

public interface DispatchPort {

    DispatchOutcome dispatch(DispatchCommand command);

    /** Retira las ofertas de un pedido cancelado. */
    void cancel(UUID assistanceId);
}
