package com.uade.mecanicosya.assistance.config;

import com.uade.mecanicosya.assistance.application.RequestPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
public class AssistanceConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RequestPolicy requestPolicy(
            @Value("${assistance.requests.max-age:PT15M}") Duration maxAge,
            @Value("${assistance.requests.max-clock-skew:PT5M}") Duration maxClockSkew
    ) {
        return new RequestPolicy(maxAge, maxClockSkew);
    }
}
