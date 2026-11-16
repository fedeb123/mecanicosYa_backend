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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MechanicFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(MechanicFacade.class);
    private static final double KM_PER_DEGREE = 111.32;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int MAX_NEARBY = 30;

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

    /**
     * Mecánicos disponibles a menos de {@code radiusKm} en línea recta, del más cercano al más lejano. Es lo que ve el
     * repartidor en el mapa antes de pedir ayuda; quién recibe la oferta lo sigue decidiendo dispatch-service.
     */
    @Transactional(readOnly = true)
    public List<NearbyMechanic> findNearby(double latitude, double longitude, double radiusKm, VehicleType vehicleType) {
        double latitudeDelta = radiusKm / KM_PER_DEGREE;
        double longitudeDelta = radiusKm / (KM_PER_DEGREE * Math.max(Math.cos(Math.toRadians(latitude)), 0.01));
        return repository.findAvailableInBox(
                        latitude - latitudeDelta,
                        latitude + latitudeDelta,
                        longitude - longitudeDelta,
                        longitude + longitudeDelta
                ).stream()
                .filter(mechanic -> vehicleType == null || mechanic.getVehicleTypes().contains(vehicleType))
                .map(mechanic -> new NearbyMechanic(
                        mechanic,
                        distanceKm(latitude, longitude, mechanic.getLatitude(), mechanic.getLongitude())
                ))
                .filter(nearby -> nearby.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyMechanic::distanceKm))
                .limit(MAX_NEARBY)
                .toList();
    }

    public record NearbyMechanic(Mechanic mechanic, double distanceKm) {
    }

    static double distanceKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double dLatitude = Math.toRadians(toLatitude - fromLatitude);
        double dLongitude = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.pow(Math.sin(dLatitude / 2), 2)
                + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
                * Math.pow(Math.sin(dLongitude / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
