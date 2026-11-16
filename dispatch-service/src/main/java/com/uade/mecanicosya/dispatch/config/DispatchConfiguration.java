package com.uade.mecanicosya.dispatch.config;

import com.uade.mecanicosya.dispatch.application.OfferPolicy;
import com.uade.mecanicosya.dispatch.domain.ResilientTravelTimeEstimator;
import com.uade.mecanicosya.dispatch.domain.StraightLineTravelTimeEstimator;
import com.uade.mecanicosya.dispatch.domain.TravelTimeEstimator;
import com.uade.mecanicosya.dispatch.infrastructure.OpenRouteServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
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
            @Value("${clients.openrouteservice.profile:driving-car}") String profile,
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
}
