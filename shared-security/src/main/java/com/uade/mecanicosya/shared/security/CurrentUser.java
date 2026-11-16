package com.uade.mecanicosya.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Quién hace el request, según el token. Los controladores lo reciben como parámetro y con él validan que el
 * recurso sea del usuario: el id nunca sale de un query param.
 *
 * @param subject            el {@code sub} del token: el id del usuario, o el nombre del servicio
 * @param id                 el id del usuario; {@code null} en los tokens de servicio
 * @param verificationStatus sólo para mecánicos
 */
public record CurrentUser(String subject, UUID id, Role role, String email, String verificationStatus) {

    public static CurrentUser from(Jwt jwt) {
        Role role = Role.valueOf(jwt.getClaimAsString(TokenClaims.ROLE));
        UUID id = role == Role.SERVICE ? null : UUID.fromString(jwt.getSubject());
        return new CurrentUser(
                jwt.getSubject(),
                id,
                role,
                jwt.getClaimAsString(TokenClaims.EMAIL),
                jwt.getClaimAsString(TokenClaims.VERIFICATION_STATUS)
        );
    }

    public boolean is(Role expected) {
        return role == expected;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Un admin o otro servicio: pueden ver y operar recursos de cualquier usuario. */
    public boolean isPrivileged() {
        return role == Role.ADMIN || role == Role.SERVICE;
    }

    public boolean isApprovedMechanic() {
        return role == Role.MECHANIC && TokenClaims.APPROVED.equals(verificationStatus);
    }

    public boolean owns(UUID ownerId) {
        return id != null && id.equals(ownerId);
    }

    /** Deja pasar al dueño del recurso, a un admin y a otro servicio; al resto le devuelve 403. */
    public void requireSelfOrPrivileged(UUID ownerId) {
        if (!owns(ownerId) && !isPrivileged()) {
            throw new AccessDeniedException("This resource belongs to another user");
        }
    }

    /**
     * Resuelve el id del recurso: el del token, o el que mandó un admin. Un usuario común no puede pedir el de
     * otro.
     */
    public UUID resolveOwner(UUID requested) {
        if (requested == null) {
            if (id == null) {
                throw new IllegalArgumentException("A service or admin call must say which user it is about");
            }
            return id;
        }
        requireSelfOrPrivileged(requested);
        return requested;
    }
}
