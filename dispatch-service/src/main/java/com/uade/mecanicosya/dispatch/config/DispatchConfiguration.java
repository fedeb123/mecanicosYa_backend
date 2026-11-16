package com.uade.mecanicosya.dispatch.config;

import com.uade.mecanicosya.dispatch.application.OfferPolicy;
import com.uade.mecanicosya.dispatch.application.RouteFacade;
import com.uade.mecanicosya.dispatch.domain.ResilientTravelTimeEstimator;
import com.uade.mecanicosya.dispatch.domain.RoutePlanner;
import com.uade.mecanicosya.dispatch.domain.StraightLineTravelTimeEstimator;
import com.uade.mecanicosya.dispatch.domain.TravelTimeEstimator;
import com.uade.mecanicosya.dispatch.infrastructure.OpenRouteServiceClient;
import com.uade.mecanicosya.dispatch.infrastructure.OpenRouteServiceDirections;
import com.uade.mecanicosya.shared.security.ApiSecurity;
import com.uade.mecanicosya.shared.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Configuration
@EnableScheduling
public class DispatchConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchConfiguration.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Qué rol puede llamar a cada endpoint. Que el mecánico esté aprobado y actúe por sí mismo lo valida el
     * controlador con el token.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurity apiSecurity) throws Exception {
        String service = Role.SERVICE.name();
        String admin = Role.ADMIN.name();
        return apiSecurity.filterChain(http, authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/routes")
                .hasAnyRole(Role.RIDER.name(), Role.MECHANIC.name(), admin)
                .requestMatchers(HttpMethod.GET, "/api/dispatches/offers").hasAnyRole(Role.MECHANIC.name(), admin)
                .requestMatchers(HttpMethod.POST, "/api/dispatches/*/accept", "/api/dispatches/*/decline")
                .hasAnyRole(Role.MECHANIC.name(), admin)
                .requestMatchers(HttpMethod.POST, "/api/dispatches", "/api/dispatches/*/cancel")
                .hasAnyRole(service, admin)
                .requestMatchers(HttpMethod.GET, "/api/dispatches/*").hasAnyRole(service, admin));
    }

    @Bean
    OfferPolicy offerPolicy(
            @Value("${dispatch.offers.max-offers:3}") int maxOffers,
            @Value("${dispatch.offers.ttl:PT2M}") Duration ttl
    ) {
        return new OfferPolicy(maxOffers, ttl);
    }

    /**
     * Tiempo de llegada por calle con OpenRouteService si hay {@code ORS_API_KEY}; si no la hay o la API falla, en
     * línea recta. La clave se lee de la variable de entorno y nunca se guarda en el repo.
     */
    @Bean
    TravelTimeEstimator travelTimeEstimator(
            RestClient.Builder restClientBuilder,
            @Value("${clients.openrouteservice.api-key:}") String apiKey,
            @Value("${clients.openrouteservice.base-url:https://api.openrouteservice.org}") String baseUrl,
            @Value("${clients.openrouteservice.profile:cycling-regular}") String profile,
            @Value("${clients.openrouteservice.timeout:3s}") Duration timeout,
            @Value("${dispatch.eta.average-speed-kmh:20}") double averageSpeedKmh,
            @Value("${dispatch.eta.detour-factor:1.3}") double detourFactor
    ) {
        Optional<TravelTimeEstimator> routes = Optional.empty();
        if (apiKey.isBlank()) {
            LOGGER.info("ORS_API_KEY is not set: arrival times are estimated in a straight line");
        } else {
            LOGGER.info("Arrival times come from OpenRouteService (profile {})", profile);
            routes = Optional.of(new OpenRouteServiceClient(restClientBuilder.clone(), baseUrl, apiKey, profile,
                    timeout));
        }
        return new ResilientTravelTimeEstimator(routes,
                new StraightLineTravelTimeEstimator(averageSpeedKmh, detourFactor));
    }

    /** Ruta con pasos para la app, con el mismo perfil que el tiempo de las ofertas; sin clave, línea recta. */
    @Bean
    RouteFacade routeFacade(
            RestClient.Builder restClientBuilder,
            @Value("${clients.openrouteservice.api-key:}") String apiKey,
            @Value("${clients.openrouteservice.base-url:https://api.openrouteservice.org}") String baseUrl,
            @Value("${clients.openrouteservice.profile:cycling-regular}") String profile,
            @Value("${clients.openrouteservice.timeout:3s}") Duration timeout,
            @Value("${dispatch.eta.average-speed-kmh:20}") double averageSpeedKmh,
            @Value("${dispatch.eta.detour-factor:1.3}") double detourFactor
    ) {
        Optional<RoutePlanner> planner = apiKey.isBlank()
                ? Optional.empty()
                : Optional.of(new OpenRouteServiceDirections(restClientBuilder.clone(), baseUrl, apiKey, profile, timeout));
        return new RouteFacade(planner, averageSpeedKmh, detourFactor);
    }
}
