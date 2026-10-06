package com.uade.mecanicosya.dispatch.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NearestQualifiedMechanicStrategyTest {

    private final MatchingStrategy strategy = new NearestQualifiedMechanicStrategy();

    @Test
    void selectsTheBestNearbyAvailableCandidate() {
        MechanicCandidate near = candidate(-34.6040, -58.3820, 4.8);
        MechanicCandidate far = candidate(-34.6500, -58.4500, 5.0);

        MechanicSelection selection = strategy
                .select(-34.6037, -58.3816, List.of(far, near))
                .orElseThrow();

        assertThat(selection.mechanic().id()).isEqualTo(near.id());
        assertThat(selection.distanceKm()).isLessThan(1.0);
    }

    private MechanicCandidate candidate(double latitude, double longitude, double rating) {
        return new MechanicCandidate(
                UUID.randomUUID(),
                "Mechanic",
                "mechanic@example.com",
                latitude,
                longitude,
                true,
                rating,
                Set.of("CHAIN_REPAIR")
        );
    }
}

