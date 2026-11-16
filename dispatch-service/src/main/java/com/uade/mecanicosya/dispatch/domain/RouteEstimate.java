package com.uade.mecanicosya.dispatch.domain;

/**
 * Cuánto tiene que recorrer un mecánico hasta el pedido y cuánto tarda.
 *
 * @param distanceKm distancia por calle si {@code source} es ROUTE, en línea recta si es ESTIMATE
 */
public record RouteEstimate(double distanceKm, int etaMinutes, EtaSource source) {

    public RouteEstimate {
        if (distanceKm < 0 || etaMinutes < 0 || source == null) {
            throw new IllegalArgumentException("Invalid route estimate");
        }
    }
}
