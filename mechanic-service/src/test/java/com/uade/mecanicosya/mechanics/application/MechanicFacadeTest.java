package com.uade.mecanicosya.mechanics.application;

import com.uade.mecanicosya.mechanics.domain.Mechanic;
import com.uade.mecanicosya.mechanics.domain.MechanicFactory;
import com.uade.mecanicosya.mechanics.domain.MechanicRating;
import com.uade.mecanicosya.mechanics.domain.MechanicRatingRepository;
import com.uade.mecanicosya.mechanics.domain.MechanicRepository;
import com.uade.mecanicosya.mechanics.domain.VehicleType;
import org.junit.jupiter.api.Test;

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

class MechanicFacadeTest {

    private final MechanicRepository repository = mock(MechanicRepository.class);
    private final MechanicRatingRepository ratingRepository = mock(MechanicRatingRepository.class);
    private final MechanicEventPublisher eventPublisher = mock(MechanicEventPublisher.class);
    private final MechanicFacade facade = new MechanicFacade(
            repository, new MechanicFactory(), ratingRepository, eventPublisher);
    private final Mechanic mechanic = new MechanicFactory().create(
            UUID.randomUUID(),
            "Ada Mecánica",
            "ada@example.com",
            -34.6037,
            -58.3816,
            Set.of("CHAIN_REPAIR"),
            Set.of(VehicleType.BICYCLE)
    );
    private final UUID assistanceId = UUID.randomUUID();

    @Test
    void addsTheRatingAndRemembersTheAssistance() {
        when(repository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(repository.save(mechanic)).thenReturn(mechanic);

        Mechanic rated = facade.rate(mechanic.getId(), assistanceId, 4);

        assertThat(rated.getRating()).isEqualTo(4.0);
        verify(ratingRepository).save(any(MechanicRating.class));
    }

    @Test
    void theSameAssistanceDoesNotCountTwice() {
        when(repository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(ratingRepository.existsById(assistanceId)).thenReturn(true);

        Mechanic unchanged = facade.rate(mechanic.getId(), assistanceId, 1);

        assertThat(unchanged.getRatingCount()).isZero();
        verify(ratingRepository, never()).save(any(MechanicRating.class));
        verify(repository, never()).save(any(Mechanic.class));
    }

    @Test
    void approvingPublishesTheNewStatus() {
        when(repository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(repository.save(mechanic)).thenReturn(mechanic);

        Mechanic approved = facade.approve(mechanic.getId());

        assertThat(approved.isApproved()).isTrue();
        verify(eventPublisher).verificationChanged(mechanic);
    }

    @Test
    void anAccountHasASingleProfile() {
        when(repository.existsById(mechanic.getId())).thenReturn(true);

        assertThatThrownBy(() -> facade.register(mechanic.getId(), "Ada", "ada@example.com", null, 0, 0,
                Set.of("CHAIN_REPAIR"), null, null, null)).isInstanceOf(MechanicAlreadyRegisteredException.class);
        verify(repository, never()).save(any(Mechanic.class));
    }
}
