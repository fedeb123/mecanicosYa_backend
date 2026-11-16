package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.api.AssistanceDtos.AssignMechanicRequest;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.AssistanceResponse;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.CreateAssistanceRequest;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.StatusChangeResponse;
import com.uade.mecanicosya.assistance.application.AssistanceCreation;
import com.uade.mecanicosya.assistance.application.AssistanceDispatchCoordinator;
import com.uade.mecanicosya.assistance.application.AssistanceFacade;
import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistances")
@Tag(name = "Assistances")
public class AssistanceController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final AssistanceFacade facade;
    private final AssistanceDispatchCoordinator dispatchCoordinator;

    public AssistanceController(AssistanceFacade facade, AssistanceDispatchCoordinator dispatchCoordinator) {
        this.facade = facade;
        this.dispatchCoordinator = dispatchCoordinator;
    }

    @PostMapping
    @Operation(summary = "Create an assistance request and offer it to nearby mechanics; a repeated "
            + "Idempotency-Key returns the existing request with 200")
    public ResponseEntity<AssistanceResponse> create(
            @Parameter(description = "Unique per request; the app reuses it on every retry")
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreateAssistanceRequest request
    ) {
        AssistanceCreation creation;
        try {
            creation = facade.request(
                    request.clientId(),
                    request.vehicleId(),
                    request.description(),
                    request.problemType(),
                    request.requiredSkill(),
                    request.latitude(),
                    request.longitude(),
                    idempotencyKey,
                    request.clientCreatedAt()
            );
        } catch (DataIntegrityViolationException exception) {
            // Dos reintentos simultáneos con la misma clave: la restricción única dejó pasar uno solo.
            AssistanceRequest existing = facade.findByIdempotencyKey(request.clientId(), idempotencyKey)
                    .orElseThrow(() -> exception);
            creation = new AssistanceCreation(existing, true);
        }
        // Se vuelve a leer para devolver el estado después del despacho, no el del alta.
        AssistanceResponse response = AssistanceResponse.from(facade.find(creation.assistance().getId()));
        if (creation.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(response);
        }
        return ResponseEntity.created(URI.create("/api/assistances/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "List the assistances of a client or the jobs of a mechanic, newest first")
    public List<AssistanceResponse> list(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID mechanicId
    ) {
        if ((clientId == null) == (mechanicId == null)) {
            throw new IllegalArgumentException("Pass exactly one of clientId or mechanicId");
        }
        List<AssistanceRequest> assistances = clientId != null
                ? facade.findByClient(clientId)
                : facade.findByMechanic(mechanicId);
        return assistances.stream().map(AssistanceResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the current assistance state")
    public AssistanceResponse find(@PathVariable UUID id) {
        return AssistanceResponse.from(facade.find(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get the status history of an assistance")
    public List<StatusChangeResponse> history(@PathVariable UUID id) {
        return facade.history(id).stream().map(StatusChangeResponse::from).toList();
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Publish again an UNMATCHED assistance, or retry a dispatch that failed")
    public AssistanceResponse retryDispatch(@PathVariable UUID id) {
        return AssistanceResponse.from(dispatchCoordinator.dispatch(id));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Called by dispatch-service when a mechanic accepts the offer")
    public AssistanceResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignMechanicRequest request) {
        return AssistanceResponse.from(facade.assign(id, request.mechanicId()));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "The assigned mechanic starts the work")
    public AssistanceResponse start(@PathVariable UUID id) {
        return AssistanceResponse.from(facade.start(id));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark the assistance as completed")
    public AssistanceResponse complete(@PathVariable UUID id) {
        return AssistanceResponse.from(facade.complete(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an open assistance; cancelling twice is not an error")
    public AssistanceResponse cancel(@PathVariable UUID id) {
        return AssistanceResponse.from(facade.cancel(id));
    }
}
