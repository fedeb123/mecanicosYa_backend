package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.dispatch.domain.DispatchOffer;
import com.uade.mecanicosya.dispatch.domain.DispatchOfferRepository;
import com.uade.mecanicosya.dispatch.domain.DispatchRepository;
import com.uade.mecanicosya.dispatch.domain.DispatchStatus;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.dispatch.domain.NearestQualifiedMechanicStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchFacadeTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    private final DispatchRepository repository = mock(DispatchRepository.class);
    private final DispatchOfferRepository offerRepository = mock(DispatchOfferRepository.class);
    private final MechanicDirectory mechanicDirectory = mock(MechanicDirectory.class);
    private final AssistanceAssignmentPort assignmentPort = mock(AssistanceAssignmentPort.class);
    private final DispatchFacade facade = new DispatchFacade(
            repository,
            offerRepository,
            mechanicDirectory,
            new NearestQualifiedMechanicStrategy(),
            assignmentPort,
            new OfferPolicy(2, Duration.ofMinutes(2)),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );
    private final UUID assistanceId = UUID.randomUUID();

    @Test
    void offersTheAssistanceToTheBestCandidatesOfItsVehicleType() {
        MechanicCandidate near = candidate(-34.6040, -58.3820);
        MechanicCandidate far = candidate(-34.6300, -58.4100);
        MechanicCandidate farthest = candidate(-34.7000, -58.5000);
        when(repository.findById(assistanceId)).thenReturn(Optional.empty());
        when(repository.save(any(DispatchMatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mechanicDirectory.findCandidates("BRAKE_REPAIR", "MOTORCYCLE")).thenReturn(List.of(farthest, near, far));

        DispatchMatch dispatch = facade.process(command("BRAKE_REPAIR", "MOTORCYCLE"));

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.OFFERED);
        assertThat(dispatch.getMechanicId()).isNull();
        assertThat(dispatch.getOffersExpireAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        ArgumentCaptor<DispatchOffer> offers = ArgumentCaptor.forClass(DispatchOffer.class);
        verify(offerRepository, times(2)).save(offers.capture());
        assertThat(offers.getAllValues())
                .extracting(DispatchOffer::getMechanicId, DispatchOffer::getRanking)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(near.id(), 1),
                        org.assertj.core.groups.Tuple.tuple(far.id(), 2)
                );
    }

    @Test
    void savesNothingWhenMechanicServiceIsUnavailable() {
        when(repository.findById(assistanceId)).thenReturn(Optional.empty());
        when(mechanicDirectory.findCandidates("CHAIN_REPAIR", "BICYCLE"))
                .thenThrow(new MechanicDirectoryUnavailableException("down", null));

        assertThatThrownBy(() -> facade.process(command("CHAIN_REPAIR", "BICYCLE")))
                .isInstanceOf(MechanicDirectoryUnavailableException.class);
        verify(repository, never()).save(any(DispatchMatch.class));
    }

    @Test
    void anOpenDispatchIsReturnedAsItIsWhenRequestedAgain() {
        DispatchMatch open = offered(NOW.plusSeconds(60));
        when(repository.findById(assistanceId)).thenReturn(Optional.of(open));

        assertThat(facade.process(command("CHAIN_REPAIR", "BICYCLE"))).isSameAs(open);
        verify(mechanicDirectory, never()).findCandidates(any(), any());
    }

    @Test
    void anUnmatchedDispatchOpensANewRound() {
        DispatchMatch expired = offered(NOW.minusSeconds(1));
        when(repository.findById(assistanceId)).thenReturn(Optional.of(expired));
        when(mechanicDirectory.findCandidates("CHAIN_REPAIR", "BICYCLE")).thenReturn(List.of(candidate(-34.6, -58.38)));

        DispatchMatch reopened = facade.process(command("CHAIN_REPAIR", "BICYCLE"));

        assertThat(reopened.getStatus()).isEqualTo(DispatchStatus.OFFERED);
        assertThat(reopened.getRound()).isEqualTo(2);
        assertThat(reopened.getOffersExpireAt()).isAfter(NOW);
    }

    @Test
    void theLoserOfAnAcceptRaceGetsAlreadyTaken() {
        UUID loser = UUID.randomUUID();
        DispatchMatch open = offered(NOW.plusSeconds(60));
        DispatchMatch takenByOther = mock(DispatchMatch.class);
        when(takenByOther.getStatus()).thenReturn(DispatchStatus.MATCHED);
        when(takenByOther.getAssistanceId()).thenReturn(assistanceId);
        when(repository.findById(assistanceId)).thenReturn(Optional.of(open), Optional.of(takenByOther));
        when(offerRepository.findByAssistanceIdAndRoundAndMechanicId(assistanceId, 1, loser))
                .thenReturn(Optional.of(new DispatchOffer(assistanceId, 1, loser, 1.2, 2, NOW)));
        when(repository.claim(eq(assistanceId), anyInt(), eq(loser), anyDouble(), eq(NOW))).thenReturn(0);

        assertThatThrownBy(() -> facade.accept(assistanceId, loser)).isInstanceOf(OfferAlreadyTakenException.class);
        verify(assignmentPort, never()).assign(any(), any());
    }

    @Test
    void aMechanicWithoutAnOfferCannotAccept() {
        when(repository.findById(assistanceId)).thenReturn(Optional.of(offered(NOW.plusSeconds(60))));

        assertThatThrownBy(() -> facade.accept(assistanceId, UUID.randomUUID()))
                .isInstanceOf(OfferNotFoundException.class);
        verify(repository, never()).claim(any(), anyInt(), any(), anyDouble(), any());
    }

    @Test
    void declineIsIdempotent() {
        UUID mechanicId = UUID.randomUUID();
        DispatchOffer offer = new DispatchOffer(assistanceId, 1, mechanicId, 0.5, 1, NOW);
        when(repository.findById(assistanceId)).thenReturn(Optional.of(offered(NOW.plusSeconds(60))));
        when(offerRepository.findByAssistanceIdAndRoundAndMechanicId(assistanceId, 1, mechanicId))
                .thenReturn(Optional.of(offer));

        facade.decline(assistanceId, mechanicId);
        facade.decline(assistanceId, mechanicId);

        assertThat(offer.getStatus()).isEqualTo(com.uade.mecanicosya.dispatch.domain.OfferStatus.DECLINED);
    }

    private DispatchMatch offered(Instant expiresAt) {
        return DispatchMatch.offered(assistanceId, "CHAIN_REPAIR", "BICYCLE", -34.6037, -58.3816, NOW, expiresAt);
    }

    private DispatchCommand command(String skill, String vehicleType) {
        return new DispatchCommand(assistanceId, skill, -34.6037, -58.3816, vehicleType);
    }

    private static MechanicCandidate candidate(double latitude, double longitude) {
        return new MechanicCandidate(
                UUID.randomUUID(),
                "Mecánico",
                "mecanico@example.com",
                latitude,
                longitude,
                true,
                4.5,
                Set.of("BRAKE_REPAIR", "CHAIN_REPAIR")
        );
    }
}
