package com.uade.mecanicosya.mechanics.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MechanicFactoryTest {

    private final MechanicFactory factory = new MechanicFactory();

    @Test
    void normalizesEmailAndSkills() {
        Mechanic mechanic = factory.create(
                "Ada Mecánica",
                "ADA@EXAMPLE.COM",
                -34.6037,
                -58.3816,
                Set.of(" tire_repair "),
                Set.of(VehicleType.BICYCLE)
        );

        assertThat(mechanic.getEmail()).isEqualTo("ada@example.com");
        assertThat(mechanic.getSkills()).containsExactly("TIRE_REPAIR");
        assertThat(mechanic.isAvailable()).isFalse();
    }

    @Test
    void servesBicyclesWhenNoVehicleTypeIsDeclared() {
        Mechanic mechanic = factory.create(
                "Ada Mecánica",
                "ada@example.com",
                -34.6037,
                -58.3816,
                Set.of("CHAIN_REPAIR"),
                null
        );

        assertThat(mechanic.getVehicleTypes()).containsExactly(VehicleType.BICYCLE);
        assertThat(mechanic.serves(VehicleType.MOTORCYCLE)).isFalse();
    }

    @Test
    void keepsTheDeclaredVehicleTypes() {
        Mechanic mechanic = factory.create(
                "Moto Mecánico",
                "moto@example.com",
                -34.6037,
                -58.3816,
                Set.of("BRAKE_REPAIR"),
                Set.of(VehicleType.MOTORCYCLE, VehicleType.E_BIKE)
        );

        assertThat(mechanic.serves(VehicleType.MOTORCYCLE)).isTrue();
        assertThat(mechanic.serves(VehicleType.E_BIKE)).isTrue();
        assertThat(mechanic.serves(VehicleType.BICYCLE)).isFalse();
    }
}
