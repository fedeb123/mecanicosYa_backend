package com.uade.mecanicosya.dispatch.domain;

/** De dónde sale el tiempo de llegada: la ruta real por calle (OpenRouteService) o una estimación en línea recta. */
public enum EtaSource {
    ROUTE,
    ESTIMATE
}
