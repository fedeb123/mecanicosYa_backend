package com.uade.mecanicosya.dispatch.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Usa el servicio de rutas si está configurado y, si falla o no encuentra ruta para algún mecánico, la estimación en
 * línea recta. El despacho nunca se frena por la API externa.
 */
public class ResilientTravelTimeEstimator implements TravelTimeEstimator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientTravelTimeEstimator.class);

    private final Optional<TravelTimeEstimator> routes;
    private final StraightLineTravelTimeEstimator fallback;

    public ResilientTravelTimeEstimator(Optional<TravelTimeEstimator> routes, StraightLineTravelTimeEstimator fallback) {
        this.routes = routes;
        this.fallback = fallback;
    }

    @Override
    public List<RouteEstimate> estimate(double toLatitude, double toLongitude, List<MechanicCandidate> mechanics) {
        if (mechanics.isEmpty()) {
            return List.of();
        }
        if (routes.isEmpty()) {
            return fallback.estimate(toLatitude, toLongitude, mechanics);
        }
        List<RouteEstimate> fromRoutes;
        try {
            fromRoutes = routes.get().estimate(toLatitude, toLongitude, mechanics);
        } catch (RuntimeException exception) {
            LOGGER.warn("Route service failed, estimating in a straight line: {}", exception.getMessage());
            return fallback.estimate(toLatitude, toLongitude, mechanics);
        }
        List<RouteEstimate> estimates = new ArrayList<>(mechanics.size());
        for (int index = 0; index < mechanics.size(); index++) {
            RouteEstimate route = index < fromRoutes.size() ? fromRoutes.get(index) : null;
            estimates.add(route != null ? route : fallback.estimate(toLatitude, toLongitude, mechanics.get(index)));
        }
        return estimates;
    }
}
