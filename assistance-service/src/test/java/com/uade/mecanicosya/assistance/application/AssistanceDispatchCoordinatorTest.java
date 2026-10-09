package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.Client;
import com.uade.mecanicosya.assistance.domain.Vehicle;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistanceDispatchCoordinatorTest {

    private final AssistanceFacade facade = mock(AssistanceFacade.class);
    private final List<DispatchCommand> sentCommands = new ArrayList<>();
    private final AssistanceRequest assistance = newAssistance();

    @Test
    void sendsTheAssistanceDataAndAppliesTheMatch() {
        UUID mechanicId = UUID.randomUUID();
        when(facade.find(assistance.getId())).thenReturn(assistance);
        AssistanceDispatchCoordinator coordinator = coordinatorAnswering(
                new DispatchOutcome.Matched(mechanicId, 0.4)
        );

        coordinator.dispatch(assistance.getId());

        assertThat(sentCommands).singleElement().satisfies(command -> {
            assertThat(command.requiredSkill()).isEqualTo("CHAIN_REPAIR");
            assertThat(command.vehicleType()).isEqualTo(VehicleType.E_BIKE);
        });
        verify(facade).applyMatch(assistance.getId(), mechanicId);
    }

    @Test
    void leavesTheAssistanceRequestedWhenThereIsNoCandidate() {
        when(facade.find(assistance.getId())).thenReturn(assistance);
        AssistanceDispatchCoordinator coordinator = coordinatorAnswering(
                new DispatchOutcome.NoCandidate()
        );

        coordinator.dispatch(assistance.getId());

        verify(facade, never()).applyMatch(any(), any());
    }

    @Test
    void theEventListenerDoesNotFailWhenDispatchIsUnavailable() {
        when(facade.find(assistance.getId())).thenReturn(assistance);
        AssistanceDispatchCoordinator coordinator = coordinatorFailingWith(
                new DispatchUnavailableException("down", null)
        );

        assertThatCode(() -> coordinator.onAssistanceRequested(AssistanceRequestedEvent.from(assistance)))
                .doesNotThrowAnyException();
        verify(facade, never()).applyMatch(any(), any());
    }

    @Test
    void theManualRetryReportsThatDispatchIsUnavailable() {
        when(facade.find(assistance.getId())).thenReturn(assistance);
        AssistanceDispatchCoordinator coordinator = coordinatorFailingWith(
                new DispatchUnavailableException("down", null)
        );

        assertThatThrownBy(() -> coordinator.dispatch(assistance.getId()))
                .isInstanceOf(DispatchUnavailableException.class);
    }

    private AssistanceDispatchCoordinator coordinatorAnswering(DispatchOutcome outcome) {
        return new AssistanceDispatchCoordinator(facade, command -> {
            sentCommands.add(command);
            return outcome;
        });
    }

    private AssistanceDispatchCoordinator coordinatorFailingWith(RuntimeException failure) {
        return new AssistanceDispatchCoordinator(facade, command -> {
            throw failure;
        });
    }

    private static AssistanceRequest newAssistance() {
        Client client = new Client(
                UUID.randomUUID(),
                "Rider",
                "+54 11 5555-0000",
                "rider@example.com",
                Instant.parse("2026-09-28T11:00:00Z")
        );
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), client, VehicleType.E_BIKE, "Trek", "Allant+", null, null);
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
}
