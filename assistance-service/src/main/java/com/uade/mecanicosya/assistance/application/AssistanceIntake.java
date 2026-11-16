package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.application.triage.ProblemCategory;
import com.uade.mecanicosya.assistance.application.triage.Triage;
import com.uade.mecanicosya.assistance.application.triage.TriageService;
import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.Vehicle;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Recibe un pedido nuevo: lo clasifica a partir del texto y después lo crea. La clasificación (que puede llamar a la
 * API de Claude) corre fuera de la transacción del alta, para no tener una conexión a la base abierta mientras
 * espera la respuesta. Un reintento con la misma {@code Idempotency-Key} devuelve el pedido existente sin clasificar
 * de nuevo.
 */
@Service
public class AssistanceIntake {

    private final AssistanceFacade facade;
    private final ClientFacade clientFacade;
    private final TriageService triageService;

    public AssistanceIntake(AssistanceFacade facade, ClientFacade clientFacade, TriageService triageService) {
        this.facade = facade;
        this.clientFacade = clientFacade;
        this.triageService = triageService;
    }

    /**
     * @param problemType   el que eligió el repartidor, o {@code null} para que lo complete la clasificación
     * @param requiredSkill el que eligió el repartidor, o {@code null} para que lo complete la clasificación
     */
    public AssistanceCreation request(
            UUID clientId,
            UUID vehicleId,
            String description,
            String problemType,
            String requiredSkill,
            double latitude,
            double longitude,
            String idempotencyKey,
            Instant clientCreatedAt
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<AssistanceRequest> existing = facade.findByIdempotencyKey(clientId, idempotencyKey);
            if (existing.isPresent()) {
                return new AssistanceCreation(existing.get(), true);
            }
        }
        Vehicle vehicle = clientFacade.findVehicle(vehicleId);
        Triage triage = triageService.classify(description, vehicle.getType());
        String chosenProblemType = present(problemType) ? problemType : triage.problemType();
        String chosenSkill = present(requiredSkill) ? requiredSkill
                : ProblemCategory.fromProblemType(chosenProblemType)
                        .map(ProblemCategory::requiredSkill)
                        .orElse(triage.requiredSkill());
        return facade.request(
                clientId,
                vehicleId,
                description,
                chosenProblemType,
                chosenSkill,
                latitude,
                longitude,
                idempotencyKey,
                clientCreatedAt,
                triage
        );
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
