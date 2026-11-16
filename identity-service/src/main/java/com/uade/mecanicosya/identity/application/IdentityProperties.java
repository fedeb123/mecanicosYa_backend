package com.uade.mecanicosya.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * @param issuer          el {@code iss} de los tokens
 * @param accessTokenTtl  vida del access token; corta, porque no se puede revocar
 * @param refreshTokenTtl vida del refresh token, que sí se revoca
 * @param admin           el admin que se crea al arrancar si no existe; sin contraseña no se crea
 * @param serviceClients  id y secreto de cada servicio que pide tokens de servicio; un secreto vacío lo deshabilita
 */
@ConfigurationProperties("identity")
public record IdentityProperties(
        @DefaultValue("mecanicosya-identity") String issuer,
        @DefaultValue("PT15M") Duration accessTokenTtl,
        @DefaultValue("P30D") Duration refreshTokenTtl,
        @DefaultValue Admin admin,
        Map<String, String> serviceClients
) {

    public IdentityProperties {
        serviceClients = serviceClients == null ? Map.of() : Map.copyOf(serviceClients);
    }

    public record Admin(String email, String password) {
    }
}
