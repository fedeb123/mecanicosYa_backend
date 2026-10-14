package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.application.DispatchFacade;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/dispatches")
@Tag(name = "Dispatch")
public class DispatchController {

    private final DispatchFacade facade;

    public DispatchController(DispatchFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    @Operation(summary = "Match an assistance with an available qualified mechanic")
    public ResponseEntity<DispatchResponse> dispatch(@Valid @RequestBody DispatchRequest request) {
        DispatchResponse response = DispatchResponse.from(facade.process(request.toCommand()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{assistanceId}")
    @Operation(summary = "Get the asynchronous matching result")
    public DispatchResponse find(@PathVariable UUID assistanceId) {
        return DispatchResponse.from(facade.find(assistanceId));
    }
}
