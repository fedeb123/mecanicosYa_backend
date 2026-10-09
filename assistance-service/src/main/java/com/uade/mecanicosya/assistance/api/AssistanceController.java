package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.api.AssistanceDtos.AssistanceResponse;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.CreateAssistanceRequest;
import com.uade.mecanicosya.assistance.api.AssistanceDtos.StatusChangeResponse;
import com.uade.mecanicosya.assistance.application.AssistanceDispatchCoordinator;
import com.uade.mecanicosya.assistance.application.AssistanceFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistances")
@Tag(name = "Assistances")
public class AssistanceController {

    private final AssistanceFacade facade;
    private final AssistanceDispatchCoordinator dispatchCoordinator;

    public AssistanceController(AssistanceFacade facade, AssistanceDispatchCoordinator dispatchCoordinator) {
        this.facade = facade;
        this.dispatchCoordinator = dispatchCoordinator;
    }

    @PostMapping
    @Operation(summary = "Create an assistance request and publish its domain event")
    public ResponseEntity<AssistanceResponse> create(@Valid @RequestBody CreateAssistanceRequest request) {
        AssistanceResponse response = AssistanceResponse.from(facade.request(
                request.clientId(),
                request.vehicleId(),
                request.description(),
                request.problemType(),
                request.requiredSkill(),
                request.latitude(),
                request.longitude()
        ));
        return ResponseEntity.created(URI.create("/api/assistances/" + response.id())).body(response);
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
    @Operation(summary = "Retry the remote dispatch process")
    public AssistanceResponse retryDispatch(@PathVariable UUID id) {
        return AssistanceResponse.from(dispatchCoordinator.dispatch(id));
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
    @Operation(summary = "Cancel an open assistance")
    public AssistanceResponse cancel(@PathVariable UUID id) {
        return AssistanceResponse.from(facade.cancel(id));
    }
}
