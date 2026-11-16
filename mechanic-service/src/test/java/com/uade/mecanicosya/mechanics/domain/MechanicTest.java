package com.uade.mecanicosya.mechanics.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MechanicTest {

    private final Mechanic mechanic = new MechanicFactory().create(
            UUID.randomUUID(),
            "Ada Mecánica",
            "ada@example.com",
            -34.6037,
            -58.3816,
            Set.of("CHAIN_REPAIR"),
            Set.of(VehicleType.BICYCLE)
    );

    @Test
    void startsWithFiveAndNoRatings() {
        assertThat(mechanic.getRating()).isEqualTo(5.0);
        assertThat(mechanic.getRatingCount()).isZero();
    }

    @Test
    void theFirstRatingReplacesTheInitialValueAndTheNextOnesAreAveraged() {
        mechanic.addRating(3);
        assertThat(mechanic.getRating()).isEqualTo(3.0);

        mechanic.addRating(4);
        assertThat(mechanic.getRating()).isEqualTo(3.5);
        assertThat(mechanic.getRatingCount()).isEqualTo(2);
    }

    @Test
    void rejectsRatingsOutsideOneToFive() {
        assertThatThrownBy(() -> mechanic.addRating(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mechanic.getRatingCount()).isZero();
    }

    @Test
    void cannotGoAvailableUntilApproved() {
        assertThat(mechanic.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_VERIFICATION);
        assertThatThrownBy(() -> mechanic.changeAvailability(true, -34.6, -58.4))
                .isInstanceOf(MechanicNotApprovedException.class);

        mechanic.changeAvailability(false, -34.6, -58.4);
        mechanic.approve();
        mechanic.changeAvailability(true, -34.6, -58.4);

        assertThat(mechanic.isAvailable()).isTrue();
    }
}
