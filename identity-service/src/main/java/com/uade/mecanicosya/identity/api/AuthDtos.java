package com.uade.mecanicosya.identity.api;

import com.uade.mecanicosya.identity.application.AuthSession;
import com.uade.mecanicosya.identity.application.IssuedToken;
import com.uade.mecanicosya.identity.domain.UserAccount;
import com.uade.mecanicosya.identity.domain.VerificationStatus;
import com.uade.mecanicosya.shared.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** {@code role}: RIDER o MECHANIC. Los admins no se registran por la API. */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 180) String email,
            // BCrypt sólo mira los primeros 72 bytes.
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role
    ) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ServiceTokenRequest(@NotBlank String clientId, @NotBlank String clientSecret) {
    }

    /**
     * El contrato que usa la app. {@code userId} es el id del repartidor o del mecánico en los otros servicios;
     * {@code verificationStatus} es {@code null} salvo para mecánicos.
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            UUID userId,
            Role role,
            VerificationStatus verificationStatus
    ) {
        public static TokenResponse from(AuthSession session) {
            return new TokenResponse(
                    session.accessToken().value(),
                    session.refreshToken(),
                    session.accessToken().expiresAt(),
                    session.user().getId(),
                    session.user().getRole(),
                    session.user().getVerificationStatus()
            );
        }
    }

    public record ServiceTokenResponse(String accessToken, Instant expiresAt) {
        public static ServiceTokenResponse from(IssuedToken token) {
            return new ServiceTokenResponse(token.value(), token.expiresAt());
        }
    }

    public record MeResponse(UUID userId, String email, Role role, VerificationStatus verificationStatus) {
        public static MeResponse from(UserAccount user) {
            return new MeResponse(user.getId(), user.getEmail(), user.getRole(), user.getVerificationStatus());
        }
    }
}
