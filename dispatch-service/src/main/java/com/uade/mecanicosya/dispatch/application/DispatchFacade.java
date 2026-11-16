package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.application.DispatchEventPublisher.UnmatchedReason;
import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.dispatch.domain.DispatchOffer;
import com.uade.mecanicosya.dispatch.domain.DispatchOfferRepository;
import com.uade.mecanicosya.dispatch.domain.DispatchRepository;
import com.uade.mecanicosya.dispatch.domain.DispatchStatus;
import com.uade.mecanicosya.dispatch.domain.MatchingStrategy;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.dispatch.domain.MechanicSelection;
import com.uade.mecanicosya.dispatch.domain.OfferStatus;
import com.uade.mecanicosya.dispatch.domain.OpenOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DispatchFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchFacade.class);

    private final DispatchRepository repository;
    private final DispatchOfferRepository offerRepository;
    private final MechanicDirectory mechanicDirectory;
    private final MatchingStrategy matchingStrategy;
    private final AssistanceAssignmentPort assignmentPort;
    private final DispatchEventPublisher eventPublisher;
    private final OfferPolicy offerPolicy;
    private final Clock clock;

    public DispatchFacade(
            DispatchRepository repository,
            DispatchOfferRepository offerRepository,
            MechanicDirectory mechanicDirectory,
            MatchingStrategy matchingStrategy,
            AssistanceAssignmentPort assignmentPort,
            DispatchEventPublisher eventPublisher,
            OfferPolicy offerPolicy,
            Clock clock
    ) {
        this.repository = repository;
        this.offerRepository = offerRepository;
        this.mechanicDirectory = mechanicDirectory;
        this.matchingStrategy = matchingStrategy;
        this.assignmentPort = assignmentPort;
        this.eventPublisher = eventPublisher;
        this.offerPolicy = offerPolicy;
        this.clock = clock;
    }

    /**
     * Ofrece el pedido a los mejores candidatos según la estrategia. Repetir el pedido devuelve el mismo despacho,
     * salvo que haya quedado sin mecánico: en ese caso abre una ronda nueva.
     */
    @Transactional
    public DispatchMatch process(DispatchCommand command) {
        Instant now = clock.instant();
        return repository.findById(command.assistanceId())
                .map(existing -> existing.canBeOfferedAgain(now) ? offerAgain(existing, command, now) : existing)
                .orElseGet(() -> createDispatch(command, now));
    }

    @Transactional(readOnly = true)
    public DispatchMatch find(UUID assistanceId) {
        return repository.findById(assistanceId)
                .orElseThrow(() -> new DispatchNotFoundException(assistanceId));
    }

    @Transactional(readOnly = true)
    public List<DispatchOffer> offersOf(DispatchMatch dispatch) {
        return offerRepository.findByAssistanceIdAndRoundOrderByRankingAsc(dispatch.getAssistanceId(), dispatch.getRound());
    }

    @Transactional(readOnly = true)
    public List<OpenOffer> openOffers(UUID mechanicId) {
        return offerRepository.findOpenOffers(mechanicId, clock.instant());
    }

    /**
     * El mecánico toma el pedido. Gana el primero; los demás reciben {@link OfferAlreadyTakenException}. Si el
     * ganador repite el pedido (por ejemplo, porque se le cortó la conexión), recibe el mismo resultado.
     *
     * <p>El aviso a assistance-service corre dentro de la transacción: si falla, la asignación se deshace y la
     * oferta sigue abierta para reintentar.
     */
    @Transactional
    public DispatchMatch accept(UUID assistanceId, UUID mechanicId) {
        Instant now = clock.instant();
        DispatchMatch dispatch = find(assistanceId);
        if (dispatch.isTakenBy(mechanicId)) {
            return dispatch;
        }
        int round = dispatch.getRound();
        DispatchOffer offer = offerRepository.findByAssistanceIdAndRoundAndMechanicId(assistanceId, round, mechanicId)
                .orElseThrow(() -> new OfferNotFoundException(assistanceId, mechanicId));

        if (repository.claim(assistanceId, round, mechanicId, offer.getDistanceKm(), now) == 0) {
            DispatchMatch current = find(assistanceId);
            if (current.isTakenBy(mechanicId)) {
                return current;
            }
            throw rejection(current);
        }

        offerRepository.findByAssistanceIdAndRoundAndMechanicId(assistanceId, round, mechanicId)
                .ifPresent(accepted -> accepted.accept(now));
        offerRepository.closePending(assistanceId, round, OfferStatus.LOST);
        assignmentPort.assign(assistanceId, mechanicId);
        DispatchMatch matched = find(assistanceId);
        eventPublisher.matched(matched);
        LOGGER.info("Mechanic {} accepted assistance {} in round {}", mechanicId, assistanceId, round);
        return matched;
    }

    @Transactional
    public void decline(UUID assistanceId, UUID mechanicId) {
        DispatchMatch dispatch = find(assistanceId);
        DispatchOffer offer = offerRepository
                .findByAssistanceIdAndRoundAndMechanicId(assistanceId, dispatch.getRound(), mechanicId)
                .orElseThrow(() -> new OfferNotFoundException(assistanceId, mechanicId));
        offer.decline(clock.instant());
        LOGGER.info("Mechanic {} declined assistance {}", mechanicId, assistanceId);
    }

    /**
     * El repartidor canceló: se retiran las ofertas pendientes. Cancelar dos veces no cambia nada.
     */
    @Transactional
    public DispatchMatch cancel(UUID assistanceId) {
        DispatchMatch dispatch = find(assistanceId);
        if (dispatch.getStatus() != DispatchStatus.CANCELLED) {
            offerRepository.closePending(assistanceId, dispatch.getRound(), OfferStatus.WITHDRAWN);
            dispatch.cancel();
            LOGGER.info("Dispatch of assistance {} cancelled", assistanceId);
        }
        return dispatch;
    }

    @Transactional
    public int expireOffers() {
        List<DispatchMatch> expired = repository.findByStatusAndOffersExpireAtLessThanEqual(
                DispatchStatus.OFFERED,
                clock.instant()
        );
        for (DispatchMatch dispatch : expired) {
            offerRepository.closePending(dispatch.getAssistanceId(), dispatch.getRound(), OfferStatus.EXPIRED);
            dispatch.expire();
            eventPublisher.unmatched(dispatch, UnmatchedReason.EXPIRED);
        }
        return expired.size();
    }

    private DispatchMatch createDispatch(DispatchCommand command, Instant now) {
        List<MechanicSelection> ranked = rankCandidates(command);
        if (ranked.isEmpty()) {
            LOGGER.warn("No mechanic candidate found for assistance {}", command.assistanceId());
            DispatchMatch unmatched = repository.save(DispatchMatch.noCandidate(
                    command.assistanceId(),
                    command.requiredSkill(),
                    command.vehicleType(),
                    command.latitude(),
                    command.longitude(),
                    now
            ));
            eventPublisher.unmatched(unmatched, UnmatchedReason.NO_CANDIDATE);
            return unmatched;
        }
        DispatchMatch saved = repository.save(DispatchMatch.offered(
                command.assistanceId(),
                command.requiredSkill(),
                command.vehicleType(),
                command.latitude(),
                command.longitude(),
                now,
                now.plus(offerPolicy.ttl())
        ));
        saveOffers(saved, ranked, now);
        eventPublisher.offered(saved, ranked.size());
        return saved;
    }

    private DispatchMatch offerAgain(DispatchMatch existing, DispatchCommand command, Instant now) {
        offerRepository.closePending(existing.getAssistanceId(), existing.getRound(), OfferStatus.EXPIRED);
        List<MechanicSelection> ranked = rankCandidates(command);
        if (ranked.isEmpty()) {
            LOGGER.warn("No mechanic candidate found for assistance {} on a new round", command.assistanceId());
            existing.markNoCandidate();
            eventPublisher.unmatched(existing, UnmatchedReason.NO_CANDIDATE);
            return existing;
        }
        existing.reopen(now.plus(offerPolicy.ttl()));
        saveOffers(existing, ranked, now);
        eventPublisher.offered(existing, ranked.size());
        return existing;
    }

    private List<MechanicSelection> rankCandidates(DispatchCommand command) {
        LOGGER.info(
                "Dispatching assistance {}: asking mechanic-service for {} candidates",
                command.assistanceId(),
                command.requiredSkill()
        );
        List<MechanicCandidate> candidates = mechanicDirectory.findCandidates(
                command.requiredSkill(),
                command.vehicleType()
        );
        return matchingStrategy.rank(command.latitude(), command.longitude(), candidates).stream()
                .limit(offerPolicy.maxOffers())
                .toList();
    }

    private void saveOffers(DispatchMatch dispatch, List<MechanicSelection> ranked, Instant now) {
        for (int index = 0; index < ranked.size(); index++) {
            MechanicSelection selection = ranked.get(index);
            offerRepository.save(new DispatchOffer(
                    dispatch.getAssistanceId(),
                    dispatch.getRound(),
                    selection.mechanic().id(),
                    selection.distanceKm(),
                    index + 1,
                    now
            ));
        }
        LOGGER.info(
                "Assistance {} offered to {} mechanic(s) until {} (round {})",
                dispatch.getAssistanceId(),
                ranked.size(),
                dispatch.getOffersExpireAt(),
                dispatch.getRound()
        );
    }

    private static RuntimeException rejection(DispatchMatch current) {
        return switch (current.getStatus()) {
            case MATCHED -> new OfferAlreadyTakenException(current.getAssistanceId());
            case CANCELLED -> new AssistanceClosedException(current.getAssistanceId());
            case OFFERED, UNMATCHED, NO_CANDIDATE -> new OfferExpiredException(current.getAssistanceId());
        };
    }
}
