package com.uade.mecanicosya.shared.security;

/**
 * Los claims propios que identity-service pone en el access token, además de {@code sub}, {@code iss} y las fechas.
 */
public final class TokenClaims {

    public static final String ROLE = "role";
    public static final String EMAIL = "email";
    /** Sólo en los tokens de mecánicos: {@code PENDING_VERIFICATION} o {@code APPROVED}. */
    public static final String VERIFICATION_STATUS = "verification_status";

    public static final String APPROVED = "APPROVED";
    public static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";

    /** Authority extra de un mecánico aprobado: lo exigen las ofertas y la aceptación. */
    public static final String APPROVED_MECHANIC = "MECHANIC_APPROVED";

    private TokenClaims() {
    }
}
