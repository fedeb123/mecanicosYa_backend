package com.uade.mecanicosya.mechanics.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MechanicFactoryTest {

    private final MechanicFactory factory = new MechanicFactory();

    @Test
    void normalizesEmailAndSkills() {
        Mechanic mechanic = factory.create(
                UUID.randomUUID(),
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
                UUID.randomUUID(),
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
                UUID.randomUUID(),
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

    @Test
    void aWorkshopKeepsItsAddressAndDoesNotMoveWhenItGoesAvailable() {
        Mechanic workshop = new MechanicFactory().create(java.util.UUID.randomUUID(), "Taller Sur", "sur@example.com",
                -34.62, -58.37, java.util.Set.of("TIRE_REPAIR"), null);
        workshop.changeKind(MechanicKind.WORKSHOP, " Defensa 1200 ");
        workshop.approve();

        workshop.changeAvailability(true, -34.60, -58.38);

        org.assertj.core.api.Assertions.assertThat(workshop.getAddress()).isEqualTo("Defensa 1200");
        org.assertj.core.api.Assertions.assertThat(workshop.getLatitude()).isEqualTo(-34.62);
        org.assertj.core.api.Assertions.assertThat(workshop.getLongitude()).isEqualTo(-58.37);
    }

    @Test
    void aMechanicWithoutKindIsMobileAndFollowsItsLocation() {
        Mechanic mobile = new MechanicFactory().create(java.util.UUID.randomUUID(), "Ada", "ada2@example.com",
                -34.62, -58.37, java.util.Set.of("TIRE_REPAIR"), null);
        mobile.approve();

        mobile.changeAvailability(true, -34.60, -58.38);

        org.assertj.core.api.Assertions.assertThat(mobile.getKind()).isEqualTo(MechanicKind.MOBILE);
        org.assertj.core.api.Assertions.assertThat(mobile.getLatitude()).isEqualTo(-34.60);
        org.assertj.core.api.Assertions.assertThat(mobile.getAddress()).isNull();
    }
}
