package com.uade.mecanicosya.identity.config;

import com.uade.mecanicosya.shared.security.ApiSecurity;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class IdentityConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** BCrypt, con el prefijo {@code {bcrypt}} para poder cambiar de algoritmo sin invalidar las contraseñas. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * El par de claves RSA que firma los tokens. Se genera al arrancar: no hay secreto que guardar en el repo ni en
     * el entorno. Al reiniciar el servicio los access tokens viejos dejan de valer y la app los renueva con su
     * refresh token, que vive en la base.
     */
    @Bean
    RSAKey signingKey() throws JOSEException {
        return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    }

    @Bean
    JWKSet publicKeys(RSAKey signingKey) {
        return new JWKSet(signingKey.toPublicJWK());
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /** Valida sus propios tokens en {@code GET /api/auth/me}, con la clave en memoria. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurity apiSecurity) throws Exception {
        return apiSecurity.filterChain(http, authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/service-token"
                ).permitAll());
    }
}
