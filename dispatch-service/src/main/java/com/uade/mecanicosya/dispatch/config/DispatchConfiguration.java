package com.uade.mecanicosya.dispatch.config;

import com.uade.mecanicosya.dispatch.application.OfferPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
public class DispatchConfiguration {

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
}
