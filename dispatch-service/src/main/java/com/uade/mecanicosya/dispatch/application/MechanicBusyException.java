package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

/**
 * El mecánico ya está en un trabajo: tiene que terminarlo antes de tomar otro pedido.
 */
public class MechanicBusyException extends RuntimeException {

    public MechanicBusyException(UUID mechanicId) {
        super("Mechanic %s is already working on another assistance".formatted(mechanicId));
    }
}
