package com.uade.mecanicosya.assistance.application;

import java.util.UUID;

/**
 * Resultado de pedirle un mecánico a dispatch-service. Es sellado: sólo existen estos dos casos,
 * así que un {@code switch} sobre él no necesita rama {@code default}.
 */
public sealed interface DispatchOutcome {

    record Matched(UUID mechanicId, double distanceKm) implements DispatchOutcome {
    }

    record NoCandidate() implements DispatchOutcome {
    }
}
