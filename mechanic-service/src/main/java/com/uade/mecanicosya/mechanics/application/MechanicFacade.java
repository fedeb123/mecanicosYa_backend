package com.uade.mecanicosya.mechanics.application;

import com.uade.mecanicosya.mechanics.domain.Mechanic;
import com.uade.mecanicosya.mechanics.domain.MechanicFactory;
import com.uade.mecanicosya.mechanics.domain.MechanicRating;
import com.uade.mecanicosya.mechanics.domain.MechanicRatingRepository;
import com.uade.mecanicosya.mechanics.domain.MechanicRepository;
import com.uade.mecanicosya.mechanics.domain.VehicleType;
import com.uade.mecanicosya.mechanics.domain.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MechanicFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(MechanicFacade.class);

    private final MechanicRepository repository;
    private final MechanicFactory factory;
    private final MechanicRatingRepository ratingRepository;
    private final MechanicEventPublisher eventPublisher;

    public MechanicFacade(
            MechanicRepository repository,
            MechanicFactory factory,
            MechanicRatingRepository ratingRepository,
            MechanicEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.factory = factory;
        this.ratingRepository = ratingRepository;
        this.eventPublisher = eventPublisher;
    }

    /** El perfil del mecánico de la cuenta {@code id}. Queda pendiente de validación hasta que un admin lo apruebe. */
    @Transactional
    public Mechanic register(
            UUID id,
            String name,
            String email,
            double latitude,
            double longitude,
            Set<String> skills,
            Set<VehicleType> vehicleTypes
    ) {
        if (repository.existsById(id)) {
            throw new MechanicAlreadyRegisteredException(id);
        }
        return repository.save(factory.create(id, name, email, latitude, longitude, skills, vehicleTypes));
    }

    /**
     * Aprueba al mecánico y avisa por Kafka para que su próximo token lo diga. Aprobar dos veces vuelve a publicar
     * el evento, y eso sirve para reparar una copia desactualizada en identity-service.
     */
    @Transactional
    public Mechanic approve(UUID id) {
        Mechanic mechanic = find(id);
        mechanic.approve();
        Mechanic saved = repository.save(mechanic);
        eventPublisher.verificationChanged(saved);
        LOGGER.info("Mechanic {} approved", id);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Mechanic> list(VerificationStatus status) {
        return status == null
                ? repository.findAllByOrderByNameAsc()
                : repository.findByVerificationStatusOrderByNameAsc(status);
    }

    @Transactional(readOnly = true)
    public Mechanic find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new MechanicNotFoundException(id));
    }

    @Transactional
    public Mechanic changeAvailability(UUID id, boolean available, double latitude, double longitude) {
        Mechanic mechanic = find(id);
        mechanic.changeAvailability(available, latitude, longitude);
        return repository.save(mechanic);
    }

    /**
     * Suma la calificación de una asistencia al promedio del mecánico. Es idempotente: si esa
     * asistencia ya calificó, devuelve el mecánico sin cambios.
     */
    @Transactional
    public Mechanic rate(UUID mechanicId, UUID assistanceId, int score) {
        Mechanic mechanic = find(mechanicId);
        if (ratingRepository.existsById(assistanceId)) {
            LOGGER.info("Rating for assistance {} was already applied to mechanic {}", assistanceId, mechanicId);
            return mechanic;
        }
        mechanic.addRating(score);
        ratingRepository.save(new MechanicRating(assistanceId, mechanicId, score, Instant.now()));
        LOGGER.info(
                "Mechanic {} rated {} for assistance {}; average {} over {} ratings",
                mechanicId,
                score,
                assistanceId,
                mechanic.getRating(),
                mechanic.getRatingCount()
        );
        return repository.save(mechanic);
    }

    @Transactional(readOnly = true)
    public List<Mechanic> findCandidates(String skill, VehicleType vehicleType) {
        List<Mechanic> candidates = vehicleType == null
                ? repository.findAvailableBySkill(skill)
                : repository.findAvailableBySkillAndVehicleType(skill, vehicleType);
        LOGGER.info("Found {} available mechanics for skill {} and vehicle {}", candidates.size(), skill, vehicleType);
        return candidates;
    }
}

