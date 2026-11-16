package com.uade.mecanicosya.assistance.api.soap;

import com.uade.mecanicosya.assistance.application.triage.TriageService;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * La clasificación del texto del pedido como servicio SOAP. Hace lo mismo que {@code POST /api/triage}: usa
 * el {@link TriageService}, con la IA si hay clave y el respaldo por palabras clave si no.
 */
@Endpoint
public class TriageSoapEndpoint {

    public static final String NAMESPACE = "http://mecanicosya.com/ws/triage";

    private final TriageService triageService;

    public TriageSoapEndpoint(TriageService triageService) {
        this.triageService = triageService;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "ClassifyProblemRequest")
    @ResponsePayload
    public ClassifyProblemResponse classify(@RequestPayload ClassifyProblemRequest request) {
        VehicleType vehicleType = request.getVehicleType() == null ? null : VehicleType.valueOf(request.getVehicleType());
        return ClassifyProblemResponse.from(triageService.classify(request.getDescription(), vehicleType));
    }
}
