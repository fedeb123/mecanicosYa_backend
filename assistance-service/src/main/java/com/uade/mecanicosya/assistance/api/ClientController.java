package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.api.ClientDtos.ClientResponse;
import com.uade.mecanicosya.assistance.api.ClientDtos.CreateClientRequest;
import com.uade.mecanicosya.assistance.api.ClientDtos.CreateVehicleRequest;
import com.uade.mecanicosya.assistance.api.ClientDtos.VehicleResponse;
import com.uade.mecanicosya.assistance.application.ClientFacade;
import com.uade.mecanicosya.shared.security.CurrentUser;
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
@RequestMapping("/api/clients")
@Tag(name = "Clients")
public class ClientController {

    private final ClientFacade facade;

    public ClientController(ClientFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    @Operation(summary = "Create the profile of the logged-in rider (role RIDER)",
            description = "The id and the email come from the token, so the client id is the rider's userId.")
    public ResponseEntity<ClientResponse> create(CurrentUser user, @Valid @RequestBody CreateClientRequest request) {
        ClientResponse response = ClientResponse.from(
                facade.register(user.id(), request.fullName(), request.phone(), user.email())
        );
        return ResponseEntity.created(URI.create("/api/clients/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a client (the rider themself or an admin)")
    public ClientResponse find(CurrentUser user, @PathVariable UUID id) {
        user.requireSelfOrPrivileged(id);
        return ClientResponse.from(facade.find(id));
    }

    @PostMapping("/{id}/vehicles")
    @Operation(summary = "Register a vehicle for a client (a motorcycle requires a plate; the rider themself)")
    public ResponseEntity<VehicleResponse> addVehicle(
            CurrentUser user,
            @PathVariable UUID id,
            @Valid @RequestBody CreateVehicleRequest request
    ) {
        user.requireSelfOrPrivileged(id);
        VehicleResponse response = VehicleResponse.from(facade.registerVehicle(
                id,
                request.type(),
                request.brand(),
                request.model(),
                request.color(),
                request.plate()
        ));
        return ResponseEntity.created(URI.create("/api/clients/" + id + "/vehicles/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}/vehicles")
    @Operation(summary = "List a client's vehicles (the rider themself or an admin)")
    public List<VehicleResponse> vehicles(CurrentUser user, @PathVariable UUID id) {
        user.requireSelfOrPrivileged(id);
        return facade.vehicles(id).stream().map(VehicleResponse::from).toList();
    }
}
