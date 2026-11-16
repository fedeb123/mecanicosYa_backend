package com.uade.mecanicosya.identity.domain;

/**
 * Estado de validación de un mecánico. Lo decide un admin en mechanic-service; identity-service guarda la copia
 * que pone en el token.
 */
public enum VerificationStatus {
    PENDING_VERIFICATION,
    APPROVED
}
