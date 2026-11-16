package com.uade.mecanicosya.identity.domain;

import com.uade.mecanicosya.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Las credenciales de un usuario. Su id es también el id del repartidor en assistance-service o del mecánico en
 * mechanic-service: cada servicio crea el perfil con el id que viene en el token.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private VerificationStatus verificationStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected UserAccount() {
    }

    private UserAccount(UUID id, String email, String passwordHash, Role role, Instant createdAt) {
        this.id = id;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.verificationStatus = role == Role.MECHANIC ? VerificationStatus.PENDING_VERIFICATION : null;
        this.createdAt = createdAt;
    }

    /** Alta desde la app: sólo repartidores y mecánicos. Un mecánico arranca pendiente de validación. */
    public static UserAccount register(String email, String passwordHash, Role role, Instant now) {
        if (role != Role.RIDER && role != Role.MECHANIC) {
            throw new IllegalArgumentException("Only RIDER or MECHANIC accounts can sign up");
        }
        return new UserAccount(UUID.randomUUID(), email, passwordHash, role, now);
    }

    /** El admin inicial, que se crea al arrancar con las variables de entorno. */
    public static UserAccount admin(String email, String passwordHash, Instant now) {
        return new UserAccount(UUID.randomUUID(), email, passwordHash, Role.ADMIN, now);
    }

    public void changeVerification(VerificationStatus status) {
        if (role != Role.MECHANIC) {
            throw new IllegalStateException("Only mechanics have a verification status");
        }
        this.verificationStatus = status;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
