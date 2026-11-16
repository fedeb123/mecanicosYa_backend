package com.uade.mecanicosya.shared.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.JOSEException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Firma tokens para los tests con una clave propia. El test registra {@link #decoder()} como bean y manda
 * {@code Authorization: Bearer} con estos tokens, igual que haría la app con los de identity-service.
 */
public final class TestJwt {

    private static final RSAKey KEY = generate();
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));

    private TestJwt() {
    }

    public static JwtDecoder decoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String rider(UUID id) {
        return token(id.toString(), Role.RIDER, null);
    }

    public static String approvedMechanic(UUID id) {
        return token(id.toString(), Role.MECHANIC, TokenClaims.APPROVED);
    }

    public static String pendingMechanic(UUID id) {
        return token(id.toString(), Role.MECHANIC, TokenClaims.PENDING_VERIFICATION);
    }

    public static String admin() {
        return token(UUID.randomUUID().toString(), Role.ADMIN, null);
    }

    public static String service(String name) {
        return token(name, Role.SERVICE, null);
    }

    public static String token(String subject, Role role, String verificationStatus) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("mecanicosya-identity")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim(TokenClaims.ROLE, role.name())
                .claim(TokenClaims.EMAIL, subject + "@example.com");
        if (verificationStatus != null) {
            claims.claim(TokenClaims.VERIFICATION_STATUS, verificationStatus);
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
