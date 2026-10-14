package com.uade.mecanicosya.assistance.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTest {

    private final Client client = new Client(
            UUID.randomUUID(),
            "Rider",
            "+54 11 5555-0000",
            "rider@example.com",
            Instant.parse("2026-09-28T11:00:00Z")
    );

    @Test
    void aBicycleDoesNotNeedAPlate() {
        Vehicle bicycle = new Vehicle(UUID.randomUUID(), client, VehicleType.BICYCLE, "Trek", "FX 2", null, " ");

        assertThat(bicycle.getPlate()).isNull();
        assertThat(bicycle.belongsTo(client.getId())).isTrue();
    }

    @Test
    void aMotorcycleRequiresAPlate() {
        assertThatThrownBy(() -> new Vehicle(
                UUID.randomUUID(),
                client,
                VehicleType.MOTORCYCLE,
                "Honda",
                "Wave 110",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesThePlate() {
        Vehicle motorcycle = new Vehicle(
                UUID.randomUUID(),
                client,
                VehicleType.MOTORCYCLE,
                "Honda",
                "Wave 110",
                "Roja",
                "a123 bcd"
        );

        assertThat(motorcycle.getPlate()).isEqualTo("A123BCD");
    }
}
