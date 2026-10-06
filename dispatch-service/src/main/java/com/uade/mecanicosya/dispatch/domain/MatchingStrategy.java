package com.uade.mecanicosya.dispatch.domain;

import java.util.List;
import java.util.Optional;

public interface MatchingStrategy {

    Optional<MechanicSelection> select(
            double assistanceLatitude,
            double assistanceLongitude,
            List<MechanicCandidate> candidates
    );
}

