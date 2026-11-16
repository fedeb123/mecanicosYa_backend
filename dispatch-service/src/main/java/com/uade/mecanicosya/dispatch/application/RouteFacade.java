package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.GeoDistance;
import com.uade.mecanicosya.dispatch.domain.RoutePlan;
import com.uade.mecanicosya.dispatch.domain.RoutePlanner;
import com.uade.mecanicosya.dispatch.domain.RouteServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Ruta del mecánico hasta el repartidor. Con OpenRouteService es por calle y trae los pasos; sin clave, o si la API
 * no responde, se devuelve una línea recta con el mismo tiempo estimado que usa el despacho.
 */
public class RouteFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteFacade.class);

    private final Optional<RoutePlanner> planner;
    private final double averageSpeedKmh;
    private final double detourFactor;

    public RouteFacade(Optional<RoutePlanner> planner, double averageSpeedKmh, double detourFactor) {
        this.planner = planner;
        this.averageSpeedKmh = averageSpeedKmh;
        this.detourFactor = detourFactor;
    }

    public RoutePlan plan(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        if (planner.isPresent()) {
            try {
                return planner.get().plan(fromLatitude, fromLongitude, toLatitude, toLongitude);
            } catch (RouteServiceUnavailableException exception) {
                LOGGER.warn("Route falls back to a straight line: {}", exception.getMessage());
            }
        }
        return straightLine(fromLatitude, fromLongitude, toLatitude, toLongitude);
    }

    private RoutePlan straightLine(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double km = GeoDistance.haversineKm(fromLatitude, fromLongitude, toLatitude, toLongitude);
        int minutes = (int) Math.max(1, Math.ceil(km * detourFactor / averageSpeedKmh * 60));
        return new RoutePlan(
                EtaSource.ESTIMATE,
                GeoDistance.round(km),
                minutes,
                List.of(new double[]{fromLatitude, fromLongitude}, new double[]{toLatitude, toLongitude}),
                List.of()
        );
    }
}
