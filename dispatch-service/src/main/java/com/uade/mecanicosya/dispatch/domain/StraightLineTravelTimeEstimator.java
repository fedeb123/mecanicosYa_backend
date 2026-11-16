package com.uade.mecanicosya.dispatch.domain;

import java.util.List;

/**
 * Estimación sin red: la distancia en línea recta (haversine), estirada por un factor de desvío para aproximar el
 * recorrido por calle, a una velocidad promedio urbana. Es el respaldo cuando no hay clave de OpenRouteService o la
 * API no responde.
 */
public class StraightLineTravelTimeEstimator implements TravelTimeEstimator {

    private final double averageSpeedKmh;
    private final double detourFactor;

    public StraightLineTravelTimeEstimator(double averageSpeedKmh, double detourFactor) {
        if (averageSpeedKmh <= 0 || detourFactor < 1) {
            throw new IllegalArgumentException("The speed must be positive and the detour factor at least 1");
        }
        this.averageSpeedKmh = averageSpeedKmh;
        this.detourFactor = detourFactor;
    }

    @Override
    public List<RouteEstimate> estimate(double toLatitude, double toLongitude, List<MechanicCandidate> mechanics) {
        return mechanics.stream()
                .map(mechanic -> estimate(toLatitude, toLongitude, mechanic))
                .toList();
    }

    public RouteEstimate estimate(double toLatitude, double toLongitude, MechanicCandidate mechanic) {
        double straightKm = GeoDistance.haversineKm(mechanic.latitude(), mechanic.longitude(), toLatitude, toLongitude);
        int minutes = (int) Math.ceil(straightKm * detourFactor / averageSpeedKmh * 60);
        return new RouteEstimate(GeoDistance.round(straightKm), minutes, EtaSource.ESTIMATE);
    }
}
