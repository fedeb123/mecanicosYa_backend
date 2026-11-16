package com.uade.mecanicosya.identity.application;

import com.uade.mecanicosya.identity.domain.RefreshToken;
import com.uade.mecanicosya.identity.domain.RefreshTokenRepository;
import com.uade.mecanicosya.identity.domain.UserAccount;
import com.uade.mecanicosya.identity.domain.UserAccountRepository;
import com.uade.mecanicosya.identity.domain.VerificationStatus;
import com.uade.mecanicosya.shared.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Alta, inicio de sesión, renovación y cierre de sesión, más los tokens de servicio.
 */
@Service
public class AuthFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthFacade.class);
    private static final String INVALID_LOGIN = "Invalid email or password";

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final IdentityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    // Se compara contra este hash cuando el email no existe, para que la respuesta tarde lo mismo en los dos casos.
    private final String unknownUserHash;

    public AuthFacade(
            UserAccountRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            TokenIssuer tokenIssuer,
            IdentityProperties properties,
            Clock clock
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.properties = properties;
        this.clock = clock;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthSession register(String email, String password, Role role) {
        String normalized = UserAccount.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new EmailAlreadyRegisteredException(normalized);
        }
        UserAccount user = users.save(UserAccount.register(normalized, passwordEncoder.encode(password), role, clock.instant()));
        LOGGER.info("Registered {} account {}", user.getRole(), user.getId());
        return openSession(user);
    }

    @Transactional
    public AuthSession login(String email, String password) {
        Optional<UserAccount> user = users.findByEmail(UserAccount.normalizeEmail(email));
        boolean matches = passwordEncoder.matches(password, user.map(UserAccount::getPasswordHash).orElse(unknownUserHash));
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException(INVALID_LOGIN);
        }
        return openSession(user.get());
    }

    /**
     * Revoca el refresh token usado y emite otro. El access token nuevo trae los datos actuales del usuario: así
     * un mecánico recién aprobado recibe {@code APPROVED} sin volver a iniciar sesión.
     */
    @Transactional
    public AuthSession refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.findByTokenHash(hash(refreshToken))
                .filter(token -> token.isActive(now))
                .orElseThrow(() -> new InvalidCredentialsException("The refresh token is expired, revoked or unknown"));
        stored.revoke(now);
        UserAccount user = users.findById(stored.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("The account no longer exists"));
        return openSession(user);
    }

    /** Cerrar sesión: el refresh token deja de servir. El access token vence solo en pocos minutos. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(hash(refreshToken)).ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserAccount find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new InvalidCredentialsException("The account no longer exists"));
    }

    public IssuedToken serviceToken(String clientId, String clientSecret) {
        String expected = properties.serviceClients().get(clientId);
        if (expected == null || expected.isBlank() || !constantTimeEquals(expected, clientSecret)) {
            throw new InvalidCredentialsException("Unknown service or wrong secret");
        }
        return tokenIssuer.forService(clientId);
    }

    /** Copia el estado que decidió mechanic-service. Repetir el mismo evento no cambia nada. */
    @Transactional
    public void changeVerification(UUID mechanicId, VerificationStatus status) {
        users.findById(mechanicId).ifPresentOrElse(
                user -> {
                    user.changeVerification(status);
                    LOGGER.info("Mechanic {} is now {}", mechanicId, status);
                },
                () -> LOGGER.warn("Verification {} for unknown account {}; ignored", status, mechanicId)
        );
    }

    @Transactional
    public void ensureAdmin(String email, String password) {
        String normalized = UserAccount.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            return;
        }
        users.save(UserAccount.admin(normalized, passwordEncoder.encode(password), clock.instant()));
        LOGGER.info("Created the initial admin account {}", normalized);
    }

    private AuthSession openSession(UserAccount user) {
        Instant now = clock.instant();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), hash(refreshToken), now, now.plus(properties.refreshTokenTtl())));
        return new AuthSession(tokenIssuer.forUser(user), refreshToken, user);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
