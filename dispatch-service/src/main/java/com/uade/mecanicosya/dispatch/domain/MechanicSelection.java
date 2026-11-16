package com.uade.mecanicosya.dispatch.domain;

/**
 * Un candidato ordenado por la estrategia.
 *
 * @param etaMinutes cuánto tarda en llegar, o {@code null} si todavía no se calculó
 * @param etaSource  de dónde sale {@code etaMinutes}
 */
public record MechanicSelection(
        MechanicCandidate mechanic,
        double distanceKm,
        double score,
        Integer etaMinutes,
        EtaSource etaSource
) {

    public MechanicSelection(MechanicCandidate mechanic, double distanceKm, double score) {
        this(mechanic, distanceKm, score, null, null);
    }

    /** La misma selección con el tiempo de llegada; si la ruta es por calle, también su distancia. */
    public MechanicSelection withEta(RouteEstimate estimate) {
        double distance = estimate.source() == EtaSource.ROUTE ? estimate.distanceKm() : distanceKm;
        return new MechanicSelection(mechanic, distance, score, estimate.etaMinutes(), estimate.source());
    }
}
