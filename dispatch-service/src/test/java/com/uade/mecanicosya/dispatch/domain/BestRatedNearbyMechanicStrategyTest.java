package com.uade.mecanicosya.dispatch.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BestRatedNearbyMechanicStrategyTest {

    private static final double ASSISTANCE_LATITUDE = -34.6037;
    private static final double ASSISTANCE_LONGITUDE = -58.3816;

    private final MatchingStrategy strategy = new BestRatedNearbyMechanicStrategy(5);

    @Test
    void prefersTheBestRatedMechanicInsideTheRadiusOverTheNearestOne() {
        MechanicCandidate nearButLowRated = candidate(-34.6040, -58.3820, 3.9, true);
        MechanicCandidate fartherButTopRated = candidate(-34.6200, -58.4000, 4.9, true);

        MechanicSelection selection = strategy
                .select(ASSISTANCE_LATITUDE, ASSISTANCE_LONGITUDE, List.of(nearButLowRated, fartherButTopRated))
                .orElseThrow();

        assertThat(selection.mechanic().id()).isEqualTo(fartherButTopRated.id());
        assertThat(selection.distanceKm()).isBetween(1.0, 5.0);
    }

    @Test
    void ignoresMechanicsOutsideTheRadiusEvenIfTheyAreBetterRated() {
        MechanicCandidate inside = candidate(-34.6040, -58.3820, 4.0, true);
        MechanicCandidate outside = candidate(-34.7000, -58.5000, 5.0, true);

        MechanicSelection selection = strategy
                .select(ASSISTANCE_LATITUDE, ASSISTANCE_LONGITUDE, List.of(outside, inside))
                .orElseThrow();

        assertThat(selection.mechanic().id()).isEqualTo(inside.id());
    }

    @Test
    void breaksRatingTiesWithTheNearestMechanic() {
        MechanicCandidate far = candidate(-34.6200, -58.4000, 4.5, true);
        MechanicCandidate near = candidate(-34.6040, -58.3820, 4.5, true);

        MechanicSelection selection = strategy
                .select(ASSISTANCE_LATITUDE, ASSISTANCE_LONGITUDE, List.of(far, near))
                .orElseThrow();

        assertThat(selection.mechanic().id()).isEqualTo(near.id());
    }

    @Test
    void returnsNothingWhenNoAvailableMechanicIsInsideTheRadius() {
        MechanicCandidate unavailable = candidate(-34.6040, -58.3820, 5.0, false);
        MechanicCandidate outside = candidate(-34.7000, -58.5000, 5.0, true);

        assertThat(strategy.select(ASSISTANCE_LATITUDE, ASSISTANCE_LONGITUDE, List.of(unavailable, outside)))
                .isEmpty();
    }

    @Test
    void rejectsANonPositiveRadius() {
        assertThatThrownBy(() -> new BestRatedNearbyMechanicStrategy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MechanicCandidate candidate(double latitude, double longitude, double rating, boolean available) {
        return new MechanicCandidate(
                UUID.randomUUID(),
                "Mechanic",
                "mechanic@example.com",
                latitude,
                longitude,
                available,
                rating,
                Set.of("CHAIN_REPAIR")
        );
    }
}
