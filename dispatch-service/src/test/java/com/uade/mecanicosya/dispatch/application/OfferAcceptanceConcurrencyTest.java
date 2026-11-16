package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.dispatch.domain.DispatchOfferRepository;
import com.uade.mecanicosya.dispatch.domain.DispatchRepository;
import com.uade.mecanicosya.dispatch.domain.DispatchStatus;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.dispatch.domain.OfferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Aceptaciones simultáneas contra un Postgres real: la garantía de "gana el primero" la da la base, así que no
 * alcanza con mocks. Si no hay Docker, el test se saltea.
 */
@SpringBootTest(properties = {
        "clients.assistance-service.base-url=http://localhost:1",
        "dispatch.offers.max-offers=5",
        "messaging.outbox.relay.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
@Testcontainers(disabledWithoutDocker = true)
// Cada clase levanta sus contenedores: el contexto se cierra con ellos para que no queden tareas programadas
// intentando hablar con una base que ya no existe.
@DirtiesContext
class OfferAcceptanceConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean
    MechanicDirectory mechanicDirectory;

    @MockitoBean
    AssistanceAssignmentPort assignmentPort;

    @Autowired
    DispatchFacade facade;

    @Autowired
    DispatchRepository repository;

    @Autowired
    DispatchOfferRepository offerRepository;

    private final List<MechanicCandidate> mechanics = new ArrayList<>();

    @BeforeEach
    void fiveMechanicsAround() {
        reset(assignmentPort);
        mechanics.clear();
        for (int index = 0; index < 5; index++) {
            mechanics.add(new MechanicCandidate(
                    UUID.randomUUID(),
                    "Mecánico " + index,
                    "m" + index + "@example.com",
                    -34.6037 + index * 0.001,
                    -58.3816,
                    true,
                    4.5,
                    Set.of("CHAIN_REPAIR")
            ));
        }
        when(mechanicDirectory.findCandidates(eq("CHAIN_REPAIR"), any())).thenReturn(mechanics);
    }

    @Test
    void whenFiveMechanicsAcceptAtOnceOnlyOneGetsTheAssistance() throws Exception {
        UUID assistanceId = offeredAssistance();

        List<Future<UUID>> attempts = runTogether(mechanics.stream()
                .<Callable<UUID>>map(mechanic -> () -> facade.accept(assistanceId, mechanic.id()).getMechanicId())
                .toList());

        List<UUID> winners = new ArrayList<>();
        int alreadyTaken = 0;
        for (Future<UUID> attempt : attempts) {
            try {
                winners.add(attempt.get());
            } catch (java.util.concurrent.ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(OfferAlreadyTakenException.class);
                alreadyTaken++;
            }
        }
        assertThat(winners).hasSize(1);
        assertThat(alreadyTaken).isEqualTo(4);

        DispatchMatch stored = repository.findById(assistanceId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(DispatchStatus.MATCHED);
        assertThat(stored.getMechanicId()).isEqualTo(winners.getFirst());
        assertThat(offerRepository.findByAssistanceIdAndRoundOrderByRankingAsc(assistanceId, 1))
                .extracting(offer -> offer.getStatus())
                .containsExactlyInAnyOrder(
                        OfferStatus.ACCEPTED, OfferStatus.LOST, OfferStatus.LOST, OfferStatus.LOST, OfferStatus.LOST
                );
        verify(assignmentPort, times(1)).assign(assistanceId, winners.getFirst());
    }

    @Test
    void aRetriedAcceptFromTheWinnerReturnsTheSameAssignment() throws Exception {
        UUID assistanceId = offeredAssistance();
        UUID mechanicId = mechanics.getFirst().id();

        List<Future<UUID>> attempts = runTogether(List.of(
                () -> facade.accept(assistanceId, mechanicId).getMechanicId(),
                () -> facade.accept(assistanceId, mechanicId).getMechanicId(),
                () -> facade.accept(assistanceId, mechanicId).getMechanicId()
        ));

        for (Future<UUID> attempt : attempts) {
            assertThat(attempt.get()).isEqualTo(mechanicId);
        }
        verify(assignmentPort, times(1)).assign(assistanceId, mechanicId);
    }

    @Test
    void ifAssistanceServiceIsDownTheOfferStaysOpen() {
        UUID assistanceId = offeredAssistance();
        UUID mechanicId = mechanics.getFirst().id();
        doThrow(new AssistanceServiceUnavailableException("down", null))
                .when(assignmentPort).assign(assistanceId, mechanicId);

        assertThatThrownBy(() -> facade.accept(assistanceId, mechanicId))
                .isInstanceOf(AssistanceServiceUnavailableException.class);
        assertThat(repository.findById(assistanceId).orElseThrow().getStatus()).isEqualTo(DispatchStatus.OFFERED);

        reset(assignmentPort);
        assertThat(facade.accept(assistanceId, mechanicId).getMechanicId()).isEqualTo(mechanicId);
    }

    @Test
    void aCancelledAssistanceCannotBeAccepted() {
        UUID assistanceId = offeredAssistance();
        facade.cancel(assistanceId);
        facade.cancel(assistanceId);

        assertThatThrownBy(() -> facade.accept(assistanceId, mechanics.getFirst().id()))
                .isInstanceOf(AssistanceClosedException.class);
        assertThat(facade.openOffers(mechanics.getFirst().id())).isEmpty();
        verify(assignmentPort, never()).assign(any(), any());
    }

    @Test
    void theOpenOffersOfAMechanicShowTheAssistance() {
        UUID assistanceId = offeredAssistance();

        assertThat(facade.openOffers(mechanics.get(2).id()))
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.assistanceId()).isEqualTo(assistanceId);
                    assertThat(offer.ranking()).isEqualTo(3);
                    assertThat(offer.requiredSkill()).isEqualTo("CHAIN_REPAIR");
                });
    }

    @Test
    void aBusyMechanicSeesNoOffersAndCannotAcceptUntilTheJobIsCompleted() {
        UUID first = offeredAssistance();
        UUID second = offeredAssistance();
        UUID mechanicId = mechanics.getFirst().id();
        facade.accept(first, mechanicId);

        assertThat(facade.openOffers(mechanicId)).isEmpty();
        assertThatThrownBy(() -> facade.accept(second, mechanicId)).isInstanceOf(MechanicBusyException.class);
        assertThat(repository.findById(second).orElseThrow().getStatus()).isEqualTo(DispatchStatus.OFFERED);
        UUID third = offeredAssistance();
        assertThat(offerRepository.findByAssistanceIdAndRoundOrderByRankingAsc(third, 1))
                .extracting(offer -> offer.getMechanicId())
                .hasSize(4)
                .doesNotContain(mechanicId);

        facade.complete(first);
        facade.complete(first);

        assertThat(repository.findById(first).orElseThrow().getCompletedAt()).isNotNull();
        assertThat(facade.openOffers(mechanicId)).extracting(offer -> offer.assistanceId()).containsExactly(second);
        assertThat(facade.accept(second, mechanicId).getMechanicId()).isEqualTo(mechanicId);
    }

    @Test
    void aMechanicAcceptingTwoAssistancesAtOnceKeepsOnlyOne() throws Exception {
        UUID first = offeredAssistance();
        UUID second = offeredAssistance();
        UUID mechanicId = mechanics.getFirst().id();

        List<Future<UUID>> attempts = runTogether(List.of(
                () -> facade.accept(first, mechanicId).getAssistanceId(),
                () -> facade.accept(second, mechanicId).getAssistanceId()
        ));

        List<UUID> taken = new ArrayList<>();
        for (Future<UUID> attempt : attempts) {
            try {
                taken.add(attempt.get());
            } catch (java.util.concurrent.ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(MechanicBusyException.class);
            }
        }
        assertThat(taken).hasSize(1);
        UUID other = taken.getFirst().equals(first) ? second : first;
        assertThat(repository.findById(other).orElseThrow().getStatus()).isEqualTo(DispatchStatus.OFFERED);
        verify(assignmentPort, times(1)).assign(any(), eq(mechanicId));
    }

    private UUID offeredAssistance() {
        UUID assistanceId = UUID.randomUUID();
        DispatchMatch dispatch = facade.process(new DispatchCommand(assistanceId, "CHAIN_REPAIR", -34.6037, -58.3816, "BICYCLE"));
        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.OFFERED);
        return assistanceId;
    }

    private static <T> List<Future<T>> runTogether(List<Callable<T>> tasks) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
            start.countDown();
            for (Future<T> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException ignored) {
                    // cada test revisa el resultado de cada intento
                }
            }
            return futures;
        }
    }
}
