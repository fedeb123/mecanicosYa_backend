package com.uade.mecanicosya.dispatch.domain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "dispatch.matching.strategy", havingValue = "nearest", matchIfMissing = true)
public class NearestQualifiedMechanicStrategy implements MatchingStrategy {

    @Override
    public Optional<MechanicSelection> select(
            double assistanceLatitude,
            double assistanceLongitude,
            List<MechanicCandidate> candidates
    ) {
        return candidates.stream()
                .filter(MechanicCandidate::available)
                .map(candidate -> {
                    double distance = GeoDistance.haversineKm(
                            assistanceLatitude,
                            assistanceLongitude,
                            candidate.latitude(),
                            candidate.longitude()
                    );
                    double score = distance - (candidate.rating() * 0.1);
                    return new MechanicSelection(candidate, GeoDistance.round(distance), score);
                })
                .min(Comparator.comparingDouble(MechanicSelection::score));
    }
}
