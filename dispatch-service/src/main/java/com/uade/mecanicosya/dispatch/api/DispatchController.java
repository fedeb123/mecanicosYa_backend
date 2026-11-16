package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.application.DispatchFacade;
import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    @Operation(summary = "Offer an assistance to the best qualified mechanics")
    public ResponseEntity<DispatchResponse> dispatch(@Valid @RequestBody DispatchRequest request) {
        return ResponseEntity.ok(response(facade.process(request.toCommand())));
    }

    @GetMapping("/{assistanceId}")
    @Operation(summary = "Get the dispatch state and the offers of the current round")
    public DispatchResponse find(@PathVariable UUID assistanceId) {
        return response(facade.find(assistanceId));
    }

    @GetMapping("/offers")
    @Operation(summary = "List the open offers of a mechanic")
    public List<OfferResponse> openOffers(@RequestParam UUID mechanicId) {
        return facade.openOffers(mechanicId).stream().map(OfferResponse::from).toList();
    }

    @PostMapping("/{assistanceId}/accept")
    @Operation(summary = "Take an offered assistance; the first mechanic wins and the rest get 409")
    public DispatchResponse accept(@PathVariable UUID assistanceId, @Valid @RequestBody OfferAnswerRequest request) {
        return response(facade.accept(assistanceId, request.mechanicId()));
    }

    @PostMapping("/{assistanceId}/decline")
    @Operation(summary = "Pass on an offered assistance")
    public ResponseEntity<Void> decline(@PathVariable UUID assistanceId, @Valid @RequestBody OfferAnswerRequest request) {
        facade.decline(assistanceId, request.mechanicId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{assistanceId}/cancel")
    @Operation(summary = "Withdraw the offers of a cancelled assistance")
    public DispatchResponse cancel(@PathVariable UUID assistanceId) {
        return response(facade.cancel(assistanceId));
    }

    private DispatchResponse response(DispatchMatch match) {
        return DispatchResponse.from(match, facade.offersOf(match));
    }
}
