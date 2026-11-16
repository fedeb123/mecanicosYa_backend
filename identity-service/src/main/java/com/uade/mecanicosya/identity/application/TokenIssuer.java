package com.uade.mecanicosya.identity.application;

import com.uade.mecanicosya.identity.domain.UserAccount;
import com.uade.mecanicosya.shared.security.Role;
import com.uade.mecanicosya.shared.security.TokenClaims;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Firma los access tokens con la clave privada RSA del servicio. Los otros servicios los validan con la clave
 * pública que publica {@code /.well-known/jwks.json}.
 */
@Component
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final IdentityProperties properties;
    private final Clock clock;

    public TokenIssuer(JwtEncoder encoder, IdentityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken forUser(UserAccount user) {
        JwtClaimsSet.Builder claims = base(user.getId().toString(), user.getRole())
                .claim(TokenClaims.EMAIL, user.getEmail());
        if (user.getVerificationStatus() != null) {
            claims.claim(TokenClaims.VERIFICATION_STATUS, user.getVerificationStatus().name());
        }
        return encode(claims);
    }

    public IssuedToken forService(String serviceName) {
        return encode(base(serviceName, Role.SERVICE));
    }

    private JwtClaimsSet.Builder base(String subject, Role role) {
        Instant now = clock.instant();
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(TokenClaims.ROLE, role.name());
    }

    private IssuedToken encode(JwtClaimsSet.Builder claims) {
        JwtClaimsSet set = claims.build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, set)).getTokenValue();
        return new IssuedToken(value, set.getExpiresAt());
    }
}
