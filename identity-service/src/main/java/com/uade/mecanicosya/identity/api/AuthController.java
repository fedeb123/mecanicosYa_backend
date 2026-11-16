package com.uade.mecanicosya.identity.api;

import com.uade.mecanicosya.identity.api.AuthDtos.LoginRequest;
import com.uade.mecanicosya.identity.api.AuthDtos.MeResponse;
import com.uade.mecanicosya.identity.api.AuthDtos.RefreshRequest;
import com.uade.mecanicosya.identity.api.AuthDtos.RegisterRequest;
import com.uade.mecanicosya.identity.api.AuthDtos.ServiceTokenRequest;
import com.uade.mecanicosya.identity.api.AuthDtos.ServiceTokenResponse;
import com.uade.mecanicosya.identity.api.AuthDtos.TokenResponse;
import com.uade.mecanicosya.identity.application.AuthFacade;
import com.uade.mecanicosya.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthFacade facade;

    public AuthController(AuthFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/register")
    @Operation(summary = "Sign up as RIDER or MECHANIC and get a session; a mechanic starts PENDING_VERIFICATION",
            description = "The returned userId is the id to use when creating the rider or mechanic profile.")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TokenResponse.from(facade.register(request.email(), request.password(), request.role())));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password; 401 if either is wrong")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(facade.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Trade a refresh token for a new session; the old refresh token stops working",
            description = "The new access token carries the current verification status of a mechanic.")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(facade.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        facade.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "The account of the access token")
    public MeResponse me(CurrentUser user) {
        return MeResponse.from(facade.find(user.id()));
    }

    @PostMapping("/service-token")
    @Operation(summary = "Token with role SERVICE for calls between services (client id and secret)")
    public ServiceTokenResponse serviceToken(@Valid @RequestBody ServiceTokenRequest request) {
        return ServiceTokenResponse.from(facade.serviceToken(request.clientId(), request.clientSecret()));
    }
}
