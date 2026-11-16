package com.uade.mecanicosya.dispatch.domain;

import java.util.List;
import java.util.Optional;

public interface MatchingStrategy {

    /**
     * Ordena a los candidatos que pueden tomar el pedido, del más conveniente al menos conveniente.
     * Los que no califican (no disponibles o fuera de radio) no aparecen.
     */
    List<MechanicSelection> rank(
            double assistanceLatitude,
            double assistanceLongitude,
            List<MechanicCandidate> candidates
    );

    default Optional<MechanicSelection> select(
            double assistanceLatitude,
            double assistanceLongitude,
            List<MechanicCandidate> candidates
    ) {
        return rank(assistanceLatitude, assistanceLongitude, candidates).stream().findFirst();
    }
}
