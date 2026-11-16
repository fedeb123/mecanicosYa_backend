package com.uade.mecanicosya.dispatch.domain;

import java.util.List;

/**
 * Ruta para ir del mecánico al repartidor: la línea para dibujar en el mapa y los pasos para seguirla.
 *
 * @param points  la línea, como [latitud, longitud]
 * @param steps   vacío cuando la ruta es en línea recta
 */
public record RoutePlan(
        EtaSource source,
        double distanceKm,
        int durationMinutes,
        List<double[]> points,
        List<Step> steps
) {

    /**
     * Un paso de la ruta. {@code latitude} y {@code longitude} son donde empieza: ahí la app avisa "doblá a la
     * derecha en Corrientes".
     */
    public record Step(String instruction, String street, double distanceMeters, double durationSeconds,
                       double latitude, double longitude) {
    }
}
