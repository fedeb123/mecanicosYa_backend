package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.dispatch.domain.DispatchRepository;
import com.uade.mecanicosya.dispatch.domain.DispatchStatus;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.dispatch.domain.NearestQualifiedMechanicStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchFacadeTest {

    private final DispatchRepository repository = mock(DispatchRepository.class);
    private final MechanicDirectory mechanicDirectory = mock(MechanicDirectory.class);
    private final DispatchFacade facade = new DispatchFacade(
            repository,
            mechanicDirectory,
            new NearestQualifiedMechanicStrategy()
    );

    @Test
    void asksForCandidatesOfTheAssistanceVehicleType() {
        UUID assistanceId = UUID.randomUUID();
        MechanicCandidate motorcycleMechanic = new MechanicCandidate(
                UUID.randomUUID(),
                "Moto Mecánico",
                "moto@example.com",
                -34.6040,
                -58.3820,
                true,
                4.9,
                Set.of("BRAKE_REPAIR")
        );
        when(repository.findById(assistanceId)).thenReturn(Optional.empty());
        when(repository.save(any(DispatchMatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mechanicDirectory.findCandidates("BRAKE_REPAIR", "MOTORCYCLE")).thenReturn(List.of(motorcycleMechanic));

        DispatchMatch match = facade.process(new DispatchCommand(
                assistanceId,
                "BRAKE_REPAIR",
                -34.6037,
                -58.3816,
                "MOTORCYCLE"
        ));

        verify(mechanicDirectory).findCandidates("BRAKE_REPAIR", "MOTORCYCLE");
        assertThat(match.getStatus()).isEqualTo(DispatchStatus.MATCHED);
        assertThat(match.getMechanicId()).isEqualTo(motorcycleMechanic.id());
    }

    @Test
    void savesNothingWhenMechanicServiceIsUnavailable() {
        UUID assistanceId = UUID.randomUUID();
        when(repository.findById(assistanceId)).thenReturn(Optional.empty());
        when(mechanicDirectory.findCandidates("CHAIN_REPAIR", "BICYCLE"))
                .thenThrow(new MechanicDirectoryUnavailableException("down", null));

        assertThatThrownBy(() -> facade.process(new DispatchCommand(
                assistanceId,
                "CHAIN_REPAIR",
                -34.6037,
                -58.3816,
                "BICYCLE"
        ))).isInstanceOf(MechanicDirectoryUnavailableException.class);

        verify(repository, never()).save(any(DispatchMatch.class));
    }
}
