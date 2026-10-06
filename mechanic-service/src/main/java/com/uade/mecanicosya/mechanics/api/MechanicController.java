package com.uade.mecanicosya.mechanics.api;

import com.uade.mecanicosya.mechanics.application.MechanicFacade;
import com.uade.mecanicosya.mechanics.api.MechanicDtos.AvailabilityRequest;
import com.uade.mecanicosya.mechanics.api.MechanicDtos.CreateMechanicRequest;
import com.uade.mecanicosya.mechanics.api.MechanicDtos.MechanicResponse;
import com.uade.mecanicosya.mechanics.domain.VehicleType;
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
    @Operation(summary = "Register a mechanic (vehicleTypes defaults to BICYCLE)")
    public ResponseEntity<MechanicResponse> register(@Valid @RequestBody CreateMechanicRequest request) {
        MechanicResponse response = MechanicResponse.from(facade.register(
                request.name(),
                request.email(),
                request.latitude(),
                request.longitude(),
                request.skills(),
                request.vehicleTypes()
        ));
        return ResponseEntity.created(URI.create("/api/mechanics/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a mechanic")
    public MechanicResponse find(@PathVariable UUID id) {
        return MechanicResponse.from(facade.find(id));
    }

    @PatchMapping("/{id}/availability")
    @Operation(summary = "Update availability and current location")
    public MechanicResponse updateAvailability(
            @PathVariable UUID id,
            @Valid @RequestBody AvailabilityRequest request
    ) {
        return MechanicResponse.from(facade.changeAvailability(
                id,
                request.available(),
                request.latitude(),
                request.longitude()
        ));
    }

    @GetMapping("/candidates")
    @Operation(summary = "Find available mechanics with a required skill, optionally for a vehicle type")
    public List<MechanicResponse> findCandidates(
            @RequestParam String skill,
            @RequestParam(required = false) VehicleType vehicleType
    ) {
        return facade.findCandidates(skill, vehicleType).stream().map(MechanicResponse::from).toList();
    }
}

