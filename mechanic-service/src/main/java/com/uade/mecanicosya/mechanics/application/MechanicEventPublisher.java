package com.uade.mecanicosya.mechanics.application;

import com.uade.mecanicosya.mechanics.domain.Mechanic;

public interface MechanicEventPublisher {

    /** Se llama dentro de la transacción que cambió el estado: el evento sale por el outbox. */
    void verificationChanged(Mechanic mechanic);
}
