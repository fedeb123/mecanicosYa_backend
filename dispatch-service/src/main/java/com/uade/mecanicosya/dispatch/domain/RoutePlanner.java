package com.uade.mecanicosya.dispatch.domain;

/** Calcula la ruta por calle entre dos puntos. */
public interface RoutePlanner {

    /** @throws RouteServiceUnavailableException si el servicio de rutas no responde o no encuentra ruta */
    RoutePlan plan(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude);
}
