package com.uade.mecanicosya.assistance.config;

import com.uade.mecanicosya.assistance.application.RequestPolicy;
import com.uade.mecanicosya.shared.security.ApiSecurity;
import com.uade.mecanicosya.shared.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
public class AssistanceConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Qué rol puede llamar a cada endpoint. Que el pedido sea del repartidor del token, o que el mecánico sea el
     * asignado, lo valida el controlador.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurity apiSecurity) throws Exception {
        String rider = Role.RIDER.name();
        String mechanic = Role.MECHANIC.name();
        String admin = Role.ADMIN.name();
        return apiSecurity.filterChain(http, authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/clients").hasRole(rider)
                .requestMatchers(HttpMethod.POST, "/api/assistances", "/api/assistances/cancel-by-key").hasRole(rider)
                .requestMatchers(HttpMethod.POST, "/api/assistances/*/assign").hasRole(Role.SERVICE.name())
                .requestMatchers(HttpMethod.POST, "/api/assistances/*/start", "/api/assistances/*/complete")
                .hasAnyRole(mechanic, admin)
                .requestMatchers(HttpMethod.POST, "/api/assistances/*/rating").hasRole(rider)
                .requestMatchers(HttpMethod.POST, "/api/assistances/*/cancel", "/api/assistances/*/dispatch")
                .hasAnyRole(rider, admin));
    }

    @Bean
    RequestPolicy requestPolicy(
            @Value("${assistance.requests.max-age:PT15M}") Duration maxAge,
            @Value("${assistance.requests.max-clock-skew:PT5M}") Duration maxClockSkew
    ) {
        return new RequestPolicy(maxAge, maxClockSkew);
    }
}
