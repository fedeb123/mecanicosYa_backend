package com.uade.mecanicosya.mechanics.api;

import com.uade.mecanicosya.mechanics.application.MechanicFacade;
import com.uade.mecanicosya.mechanics.api.MechanicDtos.AvailabilityRequest;
import com.uade.mecanicosya.mechanics.api.MechanicDtos.CreateMechanicRequest;
import com.uade.mecanicosya.mechanics.api.MechanicDtos.MechanicResponse;
import com.uade.mecanicosya.mechanics.api.MechanicDtos.RatingRequest;
import com.uade.mecanicosya.mechanics.domain.VehicleType;
import com.uade.mecanicosya.mechanics.domain.VerificationStatus;
import com.uade.mecanicosya.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mechanics")
@Tag(name = "Mechanics")
public class MechanicController {

    private final MechanicFacade facade;

    public MechanicController(MechanicFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    @Operation(summary = "Create the profile of the logged-in mechanic (vehicleTypes defaults to BICYCLE)",
            description = "Role MECHANIC. The id and the email come from the token. The profile starts "
                    + "PENDING_VERIFICATION until an admin approves it.")
    public ResponseEntity<MechanicResponse> register(
            CurrentUser user,
            @Valid @RequestBody CreateMechanicRequest request
    ) {
        MechanicResponse response = MechanicResponse.from(facade.register(
                user.id(),
                request.name(),
                user.email(),
                request.latitude(),
                request.longitude(),
                request.skills(),
                request.vehicleTypes()
        ));
        return ResponseEntity.created(URI.create("/api/mechanics/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "List mechanics, optionally by verification status (role ADMIN)")
    public List<MechanicResponse> list(@RequestParam(required = false) VerificationStatus verificationStatus) {
        return facade.list(verificationStatus).stream().map(MechanicResponse::from).toList();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending mechanic so they can go available and take offers (role ADMIN)",
            description = "The mechanic's next token (login or refresh) says APPROVED.")
    public MechanicResponse approve(@PathVariable UUID id) {
        return MechanicResponse.from(facade.approve(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a mechanic")
    public MechanicResponse find(@PathVariable UUID id) {
        return MechanicResponse.from(facade.find(id));
    }

    @PatchMapping("/{id}/availability")
    @Operation(summary = "Update availability and current location (the mechanic themself)",
            description = "403 while the mechanic is pending verification.")
    public MechanicResponse updateAvailability(
            CurrentUser user,
            @PathVariable UUID id,
            @Valid @RequestBody AvailabilityRequest request
    ) {
        user.requireSelfOrPrivileged(id);
        return MechanicResponse.from(facade.changeAvailability(
                id,
                request.available(),
                request.latitude(),
                request.longitude()
        ));
    }

    @PostMapping("/{id}/ratings")
    @Operation(summary = "Add the rating of a completed assistance to the mechanic's average (idempotent per assistance; "
            + "role SERVICE or ADMIN, the ratings normally arrive by Kafka)")
    public MechanicResponse rate(@PathVariable UUID id, @Valid @RequestBody RatingRequest request) {
        return MechanicResponse.from(facade.rate(id, request.assistanceId(), request.score()));
    }

    @GetMapping("/candidates")
    @Operation(summary = "Find available, approved mechanics with a required skill, optionally for a vehicle type "
            + "(role SERVICE or ADMIN)")
    public List<MechanicResponse> findCandidates(
            @RequestParam String skill,
            @RequestParam(required = false) VehicleType vehicleType
    ) {
        return facade.findCandidates(skill, vehicleType).stream().map(MechanicResponse::from).toList();
    }
}

