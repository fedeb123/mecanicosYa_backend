package com.uade.mecanicosya.dispatch.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Ordena a los mecánicos dentro de un radio máximo: primero el mejor calificado y, a igual calificación, el más cercano.
 */
@Component
@ConditionalOnProperty(name = "dispatch.matching.strategy", havingValue = "best-rated")
public class BestRatedNearbyMechanicStrategy implements MatchingStrategy {

    private final double maxDistanceKm;

    public BestRatedNearbyMechanicStrategy(
            @Value("${dispatch.matching.max-distance-km:5}") double maxDistanceKm
    ) {
        if (maxDistanceKm <= 0) {
            throw new IllegalArgumentException("dispatch.matching.max-distance-km must be positive");
        }
        this.maxDistanceKm = maxDistanceKm;
    }

    @Override
    public List<MechanicSelection> rank(
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
                    return new MechanicSelection(candidate, GeoDistance.round(distance), candidate.rating());
                })
                .filter(selection -> selection.distanceKm() <= maxDistanceKm)
                .sorted(Comparator.comparingDouble(MechanicSelection::score).reversed()
                        .thenComparingDouble(MechanicSelection::distanceKm))
                .toList();
    }
}
