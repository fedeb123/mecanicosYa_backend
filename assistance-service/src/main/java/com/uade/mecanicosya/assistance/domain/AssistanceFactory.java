package com.uade.mecanicosya.assistance.domain;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class AssistanceFactory {

    private final Clock clock;

    public AssistanceFactory() {
        this(Clock.systemUTC());
    }

    AssistanceFactory(Clock clock) {
        this.clock = clock;
    }

    public AssistanceRequest create(
            Client client,
            Vehicle vehicle,
            String description,
            String problemType,
            String requiredSkill,
            double latitude,
            double longitude
    ) {
        return new AssistanceRequest(
                UUID.randomUUID(),
                client,
                vehicle,
                description.trim(),
                problemType,
                requiredSkill,
                latitude,
                longitude,
                Instant.now(clock)
        );
    }
}
