package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.dispatch.domain.DispatchRepository;
import com.uade.mecanicosya.dispatch.domain.MatchingStrategy;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.dispatch.domain.MechanicSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DispatchFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchFacade.class);

    private final DispatchRepository repository;
    private final MechanicDirectory mechanicDirectory;
    private final MatchingStrategy matchingStrategy;

    public DispatchFacade(
            DispatchRepository repository,
            MechanicDirectory mechanicDirectory,
            MatchingStrategy matchingStrategy
    ) {
        this.repository = repository;
        this.mechanicDirectory = mechanicDirectory;
        this.matchingStrategy = matchingStrategy;
    }

    @Transactional
    public DispatchMatch process(DispatchCommand command) {
        return repository.findById(command.assistanceId()).orElseGet(() -> createMatch(command));
    }

    @Transactional(readOnly = true)
    public DispatchMatch find(UUID assistanceId) {
        return repository.findById(assistanceId)
                .orElseThrow(() -> new DispatchNotFoundException(assistanceId));
    }

    private DispatchMatch createMatch(DispatchCommand command) {
        LOGGER.info(
                "Dispatching assistance {}: asking mechanic-service for {} candidates",
                command.assistanceId(),
                command.requiredSkill()
        );
        List<MechanicCandidate> candidates = mechanicDirectory.findCandidates(
                command.requiredSkill(),
                command.vehicleType()
        );

        DispatchMatch match = matchingStrategy
                .select(command.latitude(), command.longitude(), candidates)
                .map(selection -> matched(command, selection))
                .orElseGet(() -> DispatchMatch.noCandidate(command.assistanceId()));

        DispatchMatch saved = repository.save(match);
        if (saved.getMechanicId() == null) {
            LOGGER.warn("No mechanic candidate found for assistance {}", command.assistanceId());
        } else {
            LOGGER.info(
                    "Assistance {} matched with mechanic {} at {} km out of {} candidates",
                    command.assistanceId(),
                    saved.getMechanicId(),
                    saved.getDistanceKm(),
                    candidates.size()
            );
        }
        return saved;
    }

    private DispatchMatch matched(
            DispatchCommand command,
            MechanicSelection selection
    ) {
        return DispatchMatch.matched(
                command.assistanceId(),
                selection.mechanic().id(),
                selection.distanceKm()
        );
    }
}
