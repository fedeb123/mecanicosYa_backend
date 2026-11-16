package com.uade.mecanicosya.identity.domain;

import com.uade.mecanicosya.shared.security.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountTest {

    @Test
    void aMechanicStartsPendingAndARiderHasNoVerification() {
        UserAccount mechanic = UserAccount.register(" Ada@Example.com ", "hash", Role.MECHANIC, Instant.now());
        UserAccount rider = UserAccount.register("rider@example.com", "hash", Role.RIDER, Instant.now());

        assertThat(mechanic.getEmail()).isEqualTo("ada@example.com");
        assertThat(mechanic.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_VERIFICATION);
        assertThat(rider.getVerificationStatus()).isNull();
    }

    @Test
    void nobodySignsUpAsAdminOrService() {
        assertThatThrownBy(() -> UserAccount.register("a@example.com", "hash", Role.ADMIN, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.register("a@example.com", "hash", Role.SERVICE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyMechanicsChangeVerification() {
        UserAccount rider = UserAccount.register("rider@example.com", "hash", Role.RIDER, Instant.now());

        assertThatThrownBy(() -> rider.changeVerification(VerificationStatus.APPROVED))
                .isInstanceOf(IllegalStateException.class);
    }
}
