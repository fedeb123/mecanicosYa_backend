package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.application.DispatchFacade;
import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.shared.security.CurrentUser;
import com.uade.mecanicosya.shared.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    @Operation(summary = "Offer an assistance to the best qualified mechanics (role SERVICE or ADMIN)")
    public ResponseEntity<DispatchResponse> dispatch(@Valid @RequestBody DispatchRequest request) {
        return ResponseEntity.ok(response(facade.process(request.toCommand())));
    }

    @GetMapping("/{assistanceId}")
    @Operation(summary = "Get the dispatch state and the offers of the current round (role SERVICE or ADMIN)")
    public DispatchResponse find(@PathVariable UUID assistanceId) {
        return response(facade.find(assistanceId));
    }

    @GetMapping("/offers")
    @Operation(summary = "List the open offers of the mechanic of the token; 403 while pending verification",
            description = "Empty while the mechanic is working on an accepted assistance. An admin passes mechanicId.")
    public List<OfferResponse> openOffers(CurrentUser user, @RequestParam(required = false) UUID mechanicId) {
        return facade.openOffers(approvedMechanic(user, mechanicId)).stream().map(OfferResponse::from).toList();
    }

    @PostMapping("/{assistanceId}/accept")
    @Operation(summary = "Take an offered assistance; the first mechanic wins and the rest get 409",
            description = "The mechanic is the one of the token; 403 while pending verification. A mechanic who is "
                    + "still working on another assistance gets 409 MECHANIC_BUSY. The body is optional.")
    public DispatchResponse accept(
            CurrentUser user,
            @PathVariable UUID assistanceId,
            @RequestBody(required = false) OfferAnswerRequest request
    ) {
        return response(facade.accept(assistanceId, approvedMechanic(user, requested(request))));
    }

    @PostMapping("/{assistanceId}/decline")
    @Operation(summary = "Pass on an offered assistance (the mechanic of the token; the body is optional)")
    public ResponseEntity<Void> decline(
            CurrentUser user,
            @PathVariable UUID assistanceId,
            @RequestBody(required = false) OfferAnswerRequest request
    ) {
        facade.decline(assistanceId, approvedMechanic(user, requested(request)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{assistanceId}/cancel")
    @Operation(summary = "Withdraw the offers of a cancelled assistance (role SERVICE or ADMIN)")
    public DispatchResponse cancel(@PathVariable UUID assistanceId) {
        return response(facade.cancel(assistanceId));
    }

    /**
     * El mecánico del token, que tiene que estar aprobado. Un admin puede actuar por un mecánico pasando su id.
     */
    private static UUID approvedMechanic(CurrentUser user, UUID requested) {
        if (user.is(Role.MECHANIC) && !user.isApprovedMechanic()) {
            throw new AccessDeniedException("Mechanic pending verification: an admin must approve them first");
        }
        return user.resolveOwner(requested);
    }

    private static UUID requested(OfferAnswerRequest request) {
        return request == null ? null : request.mechanicId();
    }

    private DispatchResponse response(DispatchMatch match) {
        return DispatchResponse.from(match, facade.offersOf(match));
    }
}
