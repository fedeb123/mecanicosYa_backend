package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.api.AssistanceDtos.AssignMechanicRequest;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.AssistanceResponse;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.CreateAssistanceRequest;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.RateAssistanceRequest;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.StatusChangeResponse;
import com.uade.mecanicosya.assistance.application.AssistanceCreation;
import com.uade.mecanicosya.assistance.application.AssistanceDispatchCoordinator;
import com.uade.mecanicosya.assistance.application.AssistanceFacade;
import com.uade.mecanicosya.assistance.application.AssistanceIntake;
import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.shared.security.CurrentUser;
import com.uade.mecanicosya.shared.security.Role;
import org.springframework.security.access.AccessDeniedException;
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

/**
 * El rol de cada endpoint lo controla la configuración de seguridad; acá se valida que el pedido sea del
 * repartidor del token, o que el mecánico del token sea el asignado.
 */
@RestController
@RequestMapping("/api/assistances")
@Tag(name = "Assistances")
public class AssistanceController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final AssistanceFacade facade;
    private final AssistanceIntake intake;
    private final AssistanceDispatchCoordinator dispatchCoordinator;

    public AssistanceController(
            AssistanceFacade facade,
            AssistanceIntake intake,
            AssistanceDispatchCoordinator dispatchCoordinator
    ) {
        this.facade = facade;
        this.intake = intake;
        this.dispatchCoordinator = dispatchCoordinator;
    }

    @PostMapping
    @Operation(summary = "Create an assistance request and offer it to nearby mechanics; a repeated "
            + "Idempotency-Key returns the existing request with 200 (role RIDER)",
            description = "The client is the rider of the token. The description is classified (AI with keyword "
                    + "fallback) to get the urgency. problemType and requiredSkill are optional: when missing, the "
                    + "classification fills them.")
    public ResponseEntity<AssistanceResponse> create(
            CurrentUser user,
            @Parameter(description = "Unique per request; the app reuses it on every retry")
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreateAssistanceRequest request
    ) {
        UUID clientId = user.resolveOwner(request.clientId());
        AssistanceCreation creation;
        try {
            creation = intake.request(
                    clientId,
                    request.vehicleId(),
                    request.description(),
                    request.problemType(),
                    request.requiredSkill(),
                    request.latitude(),
                    request.longitude(),
                    idempotencyKey,
                    request.clientCreatedAt(),
                    request.mechanicKind()
            );
        } catch (DataIntegrityViolationException exception) {
            // Dos reintentos simultáneos con la misma clave: la restricción única dejó pasar uno solo.
            AssistanceRequest existing = facade.findByIdempotencyKey(clientId, idempotencyKey)
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
    @Operation(summary = "List the assistances of the rider or the jobs of the mechanic of the token, newest first",
            description = "An admin passes exactly one of clientId or mechanicId.")
    public List<AssistanceResponse> list(
            CurrentUser user,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID mechanicId
    ) {
        List<AssistanceRequest> assistances = switch (user.role()) {
            case RIDER -> {
                if (mechanicId != null) {
                    throw new IllegalArgumentException("A rider lists their own assistances: do not pass mechanicId");
                }
                yield facade.findByClient(user.resolveOwner(clientId));
            }
            case MECHANIC -> {
                if (clientId != null) {
                    throw new IllegalArgumentException("A mechanic lists their own jobs: do not pass clientId");
                }
                yield facade.findByMechanic(user.resolveOwner(mechanicId));
            }
            case ADMIN, SERVICE -> {
                if ((clientId == null) == (mechanicId == null)) {
                    throw new IllegalArgumentException("Pass exactly one of clientId or mechanicId");
                }
                yield clientId != null ? facade.findByClient(clientId) : facade.findByMechanic(mechanicId);
            }
        };
        return assistances.stream().map(AssistanceResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the current assistance state (its rider, its mechanic or an admin)")
    public AssistanceResponse find(CurrentUser user, @PathVariable UUID id) {
        return AssistanceResponse.from(visibleTo(user, id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get the status history of an assistance (its rider, its mechanic or an admin)")
    public List<StatusChangeResponse> history(CurrentUser user, @PathVariable UUID id) {
        visibleTo(user, id);
        return facade.history(id).stream().map(StatusChangeResponse::from).toList();
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Publish again an UNMATCHED assistance, or retry a dispatch that failed (its rider or an admin)")
    public AssistanceResponse retryDispatch(CurrentUser user, @PathVariable UUID id) {
        ownedBy(user, id);
        return AssistanceResponse.from(dispatchCoordinator.dispatch(id));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Called by dispatch-service when a mechanic accepts the offer (role SERVICE)")
    public AssistanceResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignMechanicRequest request) {
        return AssistanceResponse.from(facade.assign(id, request.mechanicId()));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "The assigned mechanic starts the work")
    public AssistanceResponse start(CurrentUser user, @PathVariable UUID id) {
        assignedTo(user, id);
        return AssistanceResponse.from(facade.start(id));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "The assigned mechanic marks the assistance as completed")
    public AssistanceResponse complete(CurrentUser user, @PathVariable UUID id) {
        assignedTo(user, id);
        return AssistanceResponse.from(facade.complete(id));
    }

    @PostMapping("/{id}/rating")
    @Operation(summary = "The rider rates the mechanic of a completed assistance (1 to 5); updates the mechanic's average")
    public AssistanceResponse rate(
            CurrentUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RateAssistanceRequest request
    ) {
        ownedBy(user, id);
        return AssistanceResponse.from(facade.rate(id, request.score(), request.comment()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an open assistance; cancelling twice is not an error (its rider or an admin)")
    public AssistanceResponse cancel(CurrentUser user, @PathVariable UUID id) {
        ownedBy(user, id);
        return AssistanceResponse.from(facade.cancel(id));
    }

    @PostMapping("/cancel-by-key")
    @Operation(summary = "Cancel the assistance created with this Idempotency-Key, for a request the phone "
            + "does not know whether it arrived; 404 if it never did (role RIDER)")
    public AssistanceResponse cancelByKey(
            CurrentUser user,
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestParam(required = false) UUID clientId
    ) {
        return AssistanceResponse.from(facade.cancelByIdempotencyKey(user.resolveOwner(clientId), idempotencyKey));
    }

    /** El repartidor dueño, el mecánico asignado, un admin u otro servicio. */
    private AssistanceRequest visibleTo(CurrentUser user, UUID id) {
        AssistanceRequest assistance = facade.find(id);
        boolean assignedMechanic = user.is(Role.MECHANIC) && user.owns(assistance.getAssignedMechanicId());
        if (!assignedMechanic) {
            user.requireSelfOrPrivileged(assistance.getClientId());
        }
        return assistance;
    }

    private void ownedBy(CurrentUser user, UUID id) {
        user.requireSelfOrPrivileged(facade.find(id).getClientId());
    }

    private void assignedTo(CurrentUser user, UUID id) {
        UUID mechanicId = facade.find(id).getAssignedMechanicId();
        if (mechanicId != null) {
            user.requireSelfOrPrivileged(mechanicId);
        } else if (!user.isPrivileged()) {
            throw new AccessDeniedException("Only the assigned mechanic can do this");
        }
    }
}
