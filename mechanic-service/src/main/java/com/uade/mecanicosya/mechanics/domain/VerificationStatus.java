package com.uade.mecanicosya.mechanics.domain;

/**
 * Un mecánico nuevo no puede ponerse disponible hasta que un admin lo aprueba.
 */
public enum VerificationStatus {
    PENDING_VERIFICATION,
    APPROVED
}
