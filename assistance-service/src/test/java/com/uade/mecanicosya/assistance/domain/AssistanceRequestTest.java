package com.uade.mecanicosya.assistance.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistanceRequestTest {

    private final Client client = new Client(
            UUID.randomUUID(),
            "Rider",
            "+54 11 5555-0000",
            "rider@example.com",
            Instant.parse("2026-09-28T11:00:00Z")
    );
    private final Vehicle bicycle = new Vehicle(
            UUID.randomUUID(),
            client,
            VehicleType.BICYCLE,
            "Trek",
            "FX 2",
            "Negra",
            null
    );

    private AssistanceRequest newAssistance(Vehicle vehicle) {
        return new AssistanceRequest(
                UUID.randomUUID(),
                client,
                vehicle,
                "Se cortó la cadena",
                "CHAIN",
                "CHAIN_REPAIR",
                -34.6037,
                -58.3816,
                Instant.parse("2026-09-28T12:00:00Z")
        );
    }

    @Test
    void movesToMatchedWhenAMechanicIsAssigned() {
        AssistanceRequest assistance = newAssistance(bicycle);

        UUID mechanicId = UUID.randomUUID();
        assistance.assignMechanic(mechanicId);

        assertThat(assistance.getStatus()).isEqualTo(AssistanceStatus.MATCHED);
        assertThat(assistance.getAssignedMechanicId()).isEqualTo(mechanicId);
    }

    @Test
    void copiesTheVehicleTypeFromTheVehicle() {
        AssistanceRequest assistance = newAssistance(bicycle);

        assertThat(assistance.getVehicleType()).isEqualTo(VehicleType.BICYCLE);
        assertThat(assistance.getClientId()).isEqualTo(client.getId());
        assertThat(assistance.getVehicleId()).isEqualTo(bicycle.getId());
    }

    @Test
    void rejectsAVehicleFromAnotherClient() {
        Client otherClient = new Client(
                UUID.randomUUID(),
                "Other",
                "+54 11 5555-1111",
                "other@example.com",
                Instant.parse("2026-09-28T11:00:00Z")
        );
        Vehicle otherVehicle = new Vehicle(
                UUID.randomUUID(),
                otherClient,
                VehicleType.BICYCLE,
                "Giant",
                "Escape",
                null,
                null
        );

        assertThatThrownBy(() -> newAssistance(otherVehicle))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void goesThroughTheFullLifecycle() {
        AssistanceRequest assistance = newAssistance(bicycle);

        assistance.assignMechanic(UUID.randomUUID());
        assistance.start();
        assertThat(assistance.getStatus()).isEqualTo(AssistanceStatus.IN_PROGRESS);

        assistance.complete();
        assertThat(assistance.getStatus()).isEqualTo(AssistanceStatus.COMPLETED);
    }

    @Test
    void cannotStartWithoutAMechanic() {
        AssistanceRequest assistance = newAssistance(bicycle);

        assertThatThrownBy(assistance::start).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void cannotReceiveAMechanicOnceTheWorkStarted() {
        AssistanceRequest assistance = newAssistance(bicycle);
        assistance.assignMechanic(UUID.randomUUID());
        assistance.start();

        assertThatThrownBy(() -> assistance.assignMechanic(UUID.randomUUID()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void cancelsAnOpenAssistanceButNotAClosedOne() {
        AssistanceRequest open = newAssistance(bicycle);
        open.cancel();
        assertThat(open.getStatus()).isEqualTo(AssistanceStatus.CANCELLED);

        AssistanceRequest completed = newAssistance(bicycle);
        completed.assignMechanic(UUID.randomUUID());
        completed.start();
        completed.complete();
        assertThatThrownBy(completed::cancel).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void aCompletedAssistanceCanBeRatedOnce() {
        AssistanceRequest assistance = completed();

        assistance.rate(5, "  Excelente  ", Instant.parse("2026-09-28T13:00:00Z"));

        assertThat(assistance.getRating()).isEqualTo(5);
        assertThat(assistance.getRatingComment()).isEqualTo("Excelente");
        assertThatThrownBy(() -> assistance.rate(4, null, Instant.now()))
                .isInstanceOf(AssistanceAlreadyRatedException.class);
    }

    @Test
    void theRatingMustBeBetweenOneAndFive() {
        AssistanceRequest assistance = completed();

        assertThatThrownBy(() -> assistance.rate(6, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(assistance.getRating()).isNull();
    }

    @Test
    void anAssistanceInProgressCannotBeRated() {
        AssistanceRequest assistance = newAssistance(bicycle);
        assistance.assignMechanic(UUID.randomUUID());
        assistance.start();

        assertThatThrownBy(() -> assistance.rate(5, null, Instant.now()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private AssistanceRequest completed() {
        AssistanceRequest assistance = newAssistance(bicycle);
        assistance.assignMechanic(UUID.randomUUID());
        assistance.start();
        assistance.complete();
        return assistance;
    }

    @Test
    void anAssistanceNobodyAcceptedCanBePublishedAgain() {
        AssistanceRequest assistance = newAssistance(bicycle);
        assistance.awaitOffers(Instant.parse("2026-09-28T12:02:00Z"));
        assistance.markUnmatched();

        assertThat(assistance.getStatus()).isEqualTo(AssistanceStatus.UNMATCHED);
        assertThat(assistance.getOffersExpireAt()).isNull();
        assertThatThrownBy(() -> assistance.assignMechanic(UUID.randomUUID()))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assistance.republish();
        assertThat(assistance.getStatus()).isEqualTo(AssistanceStatus.REQUESTED);
    }

    @Test
    void assigningTheSameMechanicTwiceIsHarmlessButAnotherOneIsAConflict() {
        AssistanceRequest assistance = newAssistance(bicycle);
        UUID mechanicId = UUID.randomUUID();
        assistance.assignMechanic(mechanicId);
        assistance.assignMechanic(mechanicId);

        assertThat(assistance.getAssignedMechanicId()).isEqualTo(mechanicId);
        assertThatThrownBy(() -> assistance.assignMechanic(UUID.randomUUID()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void cancellingTwiceIsNotAnError() {
        AssistanceRequest assistance = newAssistance(bicycle);
        assistance.cancel();
        assistance.cancel();

        assertThat(assistance.getStatus()).isEqualTo(AssistanceStatus.CANCELLED);
    }
}
