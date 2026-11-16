package com.uade.mecanicosya.assistance.application.triage;

import com.uade.mecanicosya.assistance.domain.VehicleType;

/** Clasifica el texto libre de un pedido. Puede lanzar {@link TriageUnavailableException} si no puede responder. */
public interface ProblemClassifier {

    /**
     * @param description el texto que escribió el repartidor; es lo único del pedido que recibe el clasificador
     * @param vehicleType el tipo de vehículo, o {@code null} si no se conoce
     */
    Triage classify(String description, VehicleType vehicleType);
}
