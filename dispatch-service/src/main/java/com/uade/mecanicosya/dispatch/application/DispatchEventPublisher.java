package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.domain.DispatchMatch;

/**
 * Avisa a los otros servicios qué pasó con el despacho. La implementación escribe en el outbox, dentro de la misma
 * transacción que el cambio.
 */
public interface DispatchEventPublisher {

    void offered(DispatchMatch dispatch, int offers);

    void matched(DispatchMatch dispatch);

    void unmatched(DispatchMatch dispatch, UnmatchedReason reason);

    enum UnmatchedReason {
        NO_CANDIDATE,
        EXPIRED
    }
}
