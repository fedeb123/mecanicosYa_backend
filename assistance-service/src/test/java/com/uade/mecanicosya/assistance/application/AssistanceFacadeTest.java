package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceFactory;
import com.uade.mecanicosya.assistance.domain.AssistanceRepository;
import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.AssistanceStatus;
import com.uade.mecanicosya.assistance.domain.Client;
import com.uade.mecanicosya.assistance.domain.InvalidStatusTransitionException;
import com.uade.mecanicosya.assistance.domain.StatusChangeRepository;
import com.uade.mecanicosya.assistance.domain.Vehicle;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistanceFacadeTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:30:00Z");

    private final AssistanceRepository repository = mock(AssistanceRepository.class);
    private final ClientFacade clientFacade = mock(ClientFacade.class);
    private final List<Object> publishedEvents = new ArrayList<>();
    private final List<String> sentRatings = new ArrayList<>();
    private RuntimeException mechanicServiceFailure;
    private final AssistanceFacade facade = new AssistanceFacade(
            repository,
            mock(StatusChangeRepository.class),
            clientFacade,
            new AssistanceFactory(),
            publishedEvents::add,
            (mechanicId, assistanceId, score) -> {
                if (mechanicServiceFailure != null) {
                    throw mechanicServiceFailure;
                }
                sentRatings.add(mechanicId + "/" + assistanceId + "/" + score);
            },
            new RequestPolicy(Duration.ofMinutes(15), Duration.ofMinutes(5)),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

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
            null,
            null
    );

    @Test
    void creatingAnAssistancePublishesTheStatusChangeAndTheDispatchEvent() {
        when(clientFacade.find(client.getId())).thenReturn(client);
        when(clientFacade.findVehicle(bicycle.getId())).thenReturn(bicycle);
        when(repository.saveAndFlush(any(AssistanceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssistanceCreation creation = facade.request(
                client.getId(),
                bicycle.getId(),
                "Pinchadura",
                "TIRE",
                "TIRE_REPAIR",
                -34.6037,
                -58.3816,
                "local-1",
                NOW.minusSeconds(30)
        );
        AssistanceRequest created = creation.assistance();
        assertThat(creation.replayed()).isFalse();
        assertThat(created.getIdempotencyKey()).isEqualTo("local-1");

        assertThat(created.getStatus()).isEqualTo(AssistanceStatus.REQUESTED);
        assertThat(publishedEvents).hasSize(2);
        assertThat(publishedEvents.get(0)).isInstanceOfSatisfying(AssistanceStatusChangedEvent.class, event -> {
            assertThat(event.fromStatus()).isNull();
            assertThat(event.toStatus()).isEqualTo(AssistanceStatus.REQUESTED);
        });
        assertThat(publishedEvents.get(1)).isInstanceOfSatisfying(AssistanceRequestedEvent.class, event ->
                assertThat(event.vehicleType()).isEqualTo(VehicleType.BICYCLE));
    }

    @Test
    void aRepeatedIdempotencyKeyReturnsTheExistingAssistanceWithoutPublishingAnything() {
        AssistanceRequest existing = storedAssistance();
        when(repository.findByClient_IdAndIdempotencyKey(client.getId(), "local-1")).thenReturn(Optional.of(existing));

        AssistanceCreation creation = facade.request(
                client.getId(), bicycle.getId(), "Pinchadura", "TIRE", "TIRE_REPAIR", -34.6, -58.38, "local-1", null
        );

        assertThat(creation.replayed()).isTrue();
        assertThat(creation.assistance()).isSameAs(existing);
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void aRequestThatWaitedTooLongOnThePhoneIsRejected() {
        assertThatThrownBy(() -> facade.request(
                client.getId(), bicycle.getId(), "Pinchadura", "TIRE", "TIRE_REPAIR", -34.6, -58.38,
                "local-2", NOW.minus(Duration.ofMinutes(16))
        )).isInstanceOf(RequestExpiredException.class);
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void aRequestWithoutIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> facade.request(
                client.getId(), bicycle.getId(), "Pinchadura", "TIRE", "TIRE_REPAIR", -34.6, -58.38, " ", null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aValidTransitionPublishesTheStatusChange() {
        AssistanceRequest matched = storedAssistance();
        matched.assignMechanic(UUID.randomUUID());

        facade.start(matched.getId());

        assertThat(publishedEvents).singleElement().isInstanceOfSatisfying(
                AssistanceStatusChangedEvent.class,
                event -> {
                    assertThat(event.fromStatus()).isEqualTo(AssistanceStatus.MATCHED);
                    assertThat(event.toStatus()).isEqualTo(AssistanceStatus.IN_PROGRESS);
                }
        );
    }

    @Test
    void anInvalidTransitionPublishesNothing() {
        AssistanceRequest requested = storedAssistance();

        assertThatThrownBy(() -> facade.complete(requested.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void ratingACompletedAssistanceSendsTheScoreToTheMechanicAndSavesIt() {
        UUID mechanicId = UUID.randomUUID();
        AssistanceRequest completed = completedAssistance(mechanicId);

        AssistanceRequest rated = facade.rate(completed.getId(), 4, "Llegó rápido");

        assertThat(rated.getRating()).isEqualTo(4);
        assertThat(rated.getRatingComment()).isEqualTo("Llegó rápido");
        assertThat(sentRatings).containsExactly(mechanicId + "/" + completed.getId() + "/4");
        verify(repository).save(completed);
    }

    @Test
    void whenMechanicServiceIsDownTheRatingIsNotSaved() {
        AssistanceRequest completed = completedAssistance(UUID.randomUUID());
        mechanicServiceFailure = new MechanicServiceUnavailableException("down", null);

        assertThatThrownBy(() -> facade.rate(completed.getId(), 5, null))
                .isInstanceOf(MechanicServiceUnavailableException.class);
        verify(repository, never()).save(any(AssistanceRequest.class));
    }

    @Test
    void anAssistanceThatIsNotCompletedCannotBeRated() {
        AssistanceRequest requested = storedAssistance();

        assertThatThrownBy(() -> facade.rate(requested.getId(), 5, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(sentRatings).isEmpty();
    }

    private AssistanceRequest completedAssistance(UUID mechanicId) {
        AssistanceRequest assistance = storedAssistance();
        assistance.assignMechanic(mechanicId);
        assistance.start();
        assistance.complete();
        return assistance;
    }

    private AssistanceRequest storedAssistance() {
        AssistanceRequest assistance = new AssistanceRequest(
                UUID.randomUUID(),
                client,
                bicycle,
                "Pinchadura",
                "TIRE",
                "TIRE_REPAIR",
                -34.6037,
                -58.3816,
                Instant.parse("2026-09-28T12:00:00Z")
        );
        when(repository.findById(assistance.getId())).thenReturn(Optional.of(assistance));
        when(repository.save(any(AssistanceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return assistance;
    }
}
