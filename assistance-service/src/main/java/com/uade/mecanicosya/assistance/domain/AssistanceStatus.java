package com.uade.mecanicosya.assistance.domain;

public enum AssistanceStatus {
    /** Pedido publicado: se está ofreciendo a los mecánicos cercanos. */
    REQUESTED,
    /** Nadie lo aceptó a tiempo o no había mecánicos. El repartidor puede volver a publicarlo. */
    UNMATCHED,
    MATCHED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
