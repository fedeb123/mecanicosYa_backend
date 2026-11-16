package com.uade.mecanicosya.dispatch.domain;

import java.util.List;

/** Calcula cuánto tarda cada mecánico en llegar al pedido. */
public interface TravelTimeEstimator {

    /**
     * @return una estimación por mecánico, en el mismo orden que {@code mechanics}
     * @throws RouteServiceUnavailableException si el servicio de rutas no respondió
     */
    List<RouteEstimate> estimate(double toLatitude, double toLongitude, List<MechanicCandidate> mechanics);
}
