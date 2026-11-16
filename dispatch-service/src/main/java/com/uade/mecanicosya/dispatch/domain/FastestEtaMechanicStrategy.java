package com.uade.mecanicosya.dispatch.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Ordena por tiempo de llegada por calle: primero el que llega antes y, a igual tiempo, el mejor calificado. Para no
 * consultar la ruta de todos los mecánicos de la ciudad, sólo calcula la de los {@code max-route-candidates} más
 * cercanos en línea recta dentro de {@code max-distance-km}.
 */
@Component
@ConditionalOnProperty(name = "dispatch.matching.strategy", havingValue = "fastest-eta")
public class FastestEtaMechanicStrategy implements MatchingStrategy {

    private final TravelTimeEstimator travelTimeEstimator;
    private final double maxDistanceKm;
    private final int maxRouteCandidates;

    public FastestEtaMechanicStrategy(
            TravelTimeEstimator travelTimeEstimator,
            @Value("${dispatch.matching.max-distance-km:5}") double maxDistanceKm,
            @Value("${dispatch.matching.max-route-candidates:10}") int maxRouteCandidates
    ) {
        if (maxDistanceKm <= 0 || maxRouteCandidates <= 0) {
            throw new IllegalArgumentException("max-distance-km and max-route-candidates must be positive");
        }
        this.travelTimeEstimator = travelTimeEstimator;
        this.maxDistanceKm = maxDistanceKm;
        this.maxRouteCandidates = maxRouteCandidates;
    }

    @Override
    public List<MechanicSelection> rank(
            double assistanceLatitude,
            double assistanceLongitude,
            List<MechanicCandidate> candidates
    ) {
        List<MechanicSelection> nearest = candidates.stream()
                .filter(MechanicCandidate::available)
                .map(candidate -> {
                    double distance = GeoDistance.round(GeoDistance.haversineKm(
                            assistanceLatitude, assistanceLongitude, candidate.latitude(), candidate.longitude()));
                    return new MechanicSelection(candidate, distance, distance);
                })
                .filter(selection -> selection.distanceKm() <= maxDistanceKm)
                .sorted(Comparator.comparingDouble(MechanicSelection::distanceKm))
                .limit(maxRouteCandidates)
                .toList();
        List<RouteEstimate> estimates = travelTimeEstimator.estimate(
                assistanceLatitude,
                assistanceLongitude,
                nearest.stream().map(MechanicSelection::mechanic).toList()
        );
        return IntStream.range(0, nearest.size())
                .mapToObj(index -> {
                    MechanicSelection withEta = nearest.get(index).withEta(estimates.get(index));
                    return new MechanicSelection(withEta.mechanic(), withEta.distanceKm(), withEta.etaMinutes(),
                            withEta.etaMinutes(), withEta.etaSource());
                })
                .sorted(Comparator.comparingDouble(MechanicSelection::score)
                        .thenComparing(Comparator.comparingDouble(
                                (MechanicSelection selection) -> selection.mechanic().rating()).reversed()))
                .toList();
    }
}
