package com.uade.mecanicosya.mechanics.config;

import com.uade.mecanicosya.shared.security.ApiSecurity;
import com.uade.mecanicosya.shared.security.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

@Configuration
public class MechanicConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Qué rol puede llamar a cada endpoint. Que el mecánico sólo cambie su propia disponibilidad lo valida el
     * controlador con el id del token.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurity apiSecurity) throws Exception {
        return apiSecurity.filterChain(http, authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/mechanics").hasRole(Role.MECHANIC.name())
                .requestMatchers(HttpMethod.GET, "/api/mechanics").hasRole(Role.ADMIN.name())
                .requestMatchers(HttpMethod.GET, "/api/mechanics/nearby")
                .hasAnyRole(Role.RIDER.name(), Role.ADMIN.name())
                .requestMatchers(HttpMethod.GET, "/api/mechanics/candidates")
                .hasAnyRole(Role.SERVICE.name(), Role.ADMIN.name())
                .requestMatchers(HttpMethod.POST, "/api/mechanics/*/approve").hasRole(Role.ADMIN.name())
                .requestMatchers(HttpMethod.POST, "/api/mechanics/*/ratings")
                .hasAnyRole(Role.SERVICE.name(), Role.ADMIN.name())
                .requestMatchers(HttpMethod.PATCH, "/api/mechanics/*/availability")
                .hasAnyRole(Role.MECHANIC.name(), Role.ADMIN.name()));
    }
}
