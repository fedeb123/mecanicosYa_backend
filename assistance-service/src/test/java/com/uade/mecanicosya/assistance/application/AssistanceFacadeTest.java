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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistanceFacadeTest {

    private final AssistanceRepository repository = mock(AssistanceRepository.class);
    private final ClientFacade clientFacade = mock(ClientFacade.class);
    private final List<Object> publishedEvents = new ArrayList<>();
    private final AssistanceFacade facade = new AssistanceFacade(
            repository,
            mock(StatusChangeRepository.class),
            clientFacade,
            new AssistanceFactory(),
            publishedEvents::add
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
        when(repository.save(any(AssistanceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssistanceRequest created = facade.request(
                client.getId(),
                bicycle.getId(),
                "Pinchadura",
                "TIRE",
                "TIRE_REPAIR",
                -34.6037,
                -58.3816
        );

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
