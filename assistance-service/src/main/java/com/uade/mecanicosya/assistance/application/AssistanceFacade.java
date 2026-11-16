package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceFactory;
import com.uade.mecanicosya.assistance.domain.AssistanceRepository;
import com.uade.mecanicosya.assistance.domain.AssistanceRequest;
import com.uade.mecanicosya.assistance.domain.AssistanceStatus;
import com.uade.mecanicosya.assistance.domain.Client;
import com.uade.mecanicosya.assistance.domain.StatusChange;
import com.uade.mecanicosya.assistance.domain.StatusChangeRepository;
import com.uade.mecanicosya.assistance.domain.Vehicle;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AssistanceFacade {

    private static final int MAX_KEY_LENGTH = 100;

    private final AssistanceRepository repository;
    private final StatusChangeRepository statusChangeRepository;
    private final ClientFacade clientFacade;
    private final AssistanceFactory factory;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final MechanicRatingPort mechanicRatingPort;
    private final RequestPolicy requestPolicy;
    private final Clock clock;

    public AssistanceFacade(
            AssistanceRepository repository,
            StatusChangeRepository statusChangeRepository,
            ClientFacade clientFacade,
            AssistanceFactory factory,
            ApplicationEventPublisher applicationEventPublisher,
            MechanicRatingPort mechanicRatingPort,
            RequestPolicy requestPolicy,
            Clock clock
    ) {
        this.repository = repository;
        this.statusChangeRepository = statusChangeRepository;
        this.clientFacade = clientFacade;
        this.factory = factory;
        this.applicationEventPublisher = applicationEventPublisher;
        this.mechanicRatingPort = mechanicRatingPort;
        this.requestPolicy = requestPolicy;
        this.clock = clock;
    }

    /**
     * Crea el pedido. Si ya existe uno del mismo cliente con la misma {@code idempotencyKey}, lo devuelve sin crear
     * otro: así un reintento de la app (por ejemplo, al recuperar la conexión) no duplica el pedido.
     */
    @Transactional
    public AssistanceCreation request(
            UUID clientId,
            UUID vehicleId,
            String description,
            String problemType,
            String requiredSkill,
            double latitude,
            double longitude,
            String idempotencyKey,
            Instant clientCreatedAt
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("The idempotency key must have between 1 and " + MAX_KEY_LENGTH + " characters");
        }
        Optional<AssistanceRequest> existing = findByIdempotencyKey(clientId, idempotencyKey);
        if (existing.isPresent()) {
            return new AssistanceCreation(existing.get(), true);
        }
        rejectIfTooOld(clientCreatedAt);
        Client client = clientFacade.find(clientId);
        Vehicle vehicle = clientFacade.findVehicle(vehicleId);
        AssistanceRequest assistance = factory.create(
                client,
                vehicle,
                description,
                problemType,
                requiredSkill,
                latitude,
                longitude,
                idempotencyKey,
                clientCreatedAt
        );
        // saveAndFlush: si otro reintento con la misma clave ganó la carrera, la restricción única salta acá.
        AssistanceRequest saved = repository.saveAndFlush(assistance);
        applicationEventPublisher.publishEvent(
                AssistanceStatusChangedEvent.of(saved.getId(), null, saved.getStatus())
        );
        applicationEventPublisher.publishEvent(AssistanceRequestedEvent.from(saved));
        return new AssistanceCreation(saved, false);
    }

    @Transactional(readOnly = true)
    public Optional<AssistanceRequest> findByIdempotencyKey(UUID clientId, String idempotencyKey) {
        return repository.findByClient_IdAndIdempotencyKey(clientId, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public List<AssistanceRequest> findByClient(UUID clientId) {
        return repository.findByClient_IdOrderByCreatedAtDesc(clientId);
    }

    @Transactional(readOnly = true)
    public List<AssistanceRequest> findByMechanic(UUID mechanicId) {
        return repository.findByAssignedMechanicIdOrderByCreatedAtDesc(mechanicId);
    }

    @Transactional(readOnly = true)
    public List<UUID> findWithExpiredOffers() {
        return repository.findByStatusAndOffersExpireAtLessThanEqual(AssistanceStatus.REQUESTED, clock.instant())
                .stream()
                .map(AssistanceRequest::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssistanceRequest find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AssistanceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<StatusChange> history(UUID id) {
        find(id);
        return statusChangeRepository.findByAssistanceIdOrderByChangedAtAsc(id);
    }

    // Los métodos REQUIRES_NEW los llama el coordinador después del commit del alta, cuando ya no hay
    // una transacción que pueda confirmar cambios.

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyMatch(UUID assistanceId, UUID mechanicId) {
        transition(assistanceId, assistance -> assistance.assignMechanic(mechanicId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awaitOffers(UUID assistanceId, Instant expiresAt) {
        transition(assistanceId, assistance -> assistance.awaitOffers(expiresAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnmatched(UUID assistanceId) {
        transition(assistanceId, AssistanceRequest::markUnmatched);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AssistanceRequest republish(UUID assistanceId) {
        return transition(assistanceId, AssistanceRequest::republish);
    }

    /**
     * dispatch-service avisa qué mecánico aceptó. Repetir el aviso con el mismo mecánico no cambia nada.
     */
    @Transactional
    public AssistanceRequest assign(UUID assistanceId, UUID mechanicId) {
        return transition(assistanceId, assistance -> assistance.assignMechanic(mechanicId));
    }

    @Transactional
    public AssistanceRequest start(UUID id) {
        return transition(id, AssistanceRequest::start);
    }

    @Transactional
    public AssistanceRequest complete(UUID id) {
        return transition(id, AssistanceRequest::complete);
    }

    @Transactional
    public AssistanceRequest cancel(UUID id) {
        return transition(id, AssistanceRequest::cancel);
    }

    /**
     * Califica al mecánico de una asistencia terminada. Primero valida en el dominio, después
     * avisa a mechanic-service y recién entonces guarda: si el otro servicio no responde, la
     * transacción se revierte y el cliente puede reintentar sin duplicar la calificación.
     */
    @Transactional
    public AssistanceRequest rate(UUID id, int score, String comment) {
        AssistanceRequest assistance = find(id);
        assistance.rate(score, comment, clock.instant());
        mechanicRatingPort.rate(assistance.getAssignedMechanicId(), assistance.getId(), score);
        return repository.save(assistance);
    }

    /**
     * Cancela el pedido que el teléfono mandó sin saber si llegó. Si nunca llegó, no hay nada que cancelar.
     */
    @Transactional
    public AssistanceRequest cancelByIdempotencyKey(UUID clientId, String idempotencyKey) {
        AssistanceRequest assistance = repository.findByClient_IdAndIdempotencyKey(clientId, idempotencyKey)
                .orElseThrow(() -> AssistanceNotFoundException.forIdempotencyKey(idempotencyKey));
        return transition(assistance.getId(), AssistanceRequest::cancel);
    }

    private void rejectIfTooOld(Instant clientCreatedAt) {
        if (clientCreatedAt == null) {
            return;
        }
        Instant now = clock.instant();
        if (clientCreatedAt.isAfter(now.plus(requestPolicy.maxClockSkew()))) {
            throw new IllegalArgumentException("clientCreatedAt is in the future: " + clientCreatedAt);
        }
        Duration age = Duration.between(clientCreatedAt, now);
        if (age.compareTo(requestPolicy.maxAge()) > 0) {
            throw new RequestExpiredException(age, requestPolicy.maxAge());
        }
    }

    private AssistanceRequest transition(UUID id, Consumer<AssistanceRequest> change) {
        AssistanceRequest assistance = find(id);
        AssistanceStatus previous = assistance.getStatus();
        change.accept(assistance);
        AssistanceRequest saved = repository.save(assistance);
        if (previous != saved.getStatus()) {
            applicationEventPublisher.publishEvent(
                    AssistanceStatusChangedEvent.of(saved.getId(), previous, saved.getStatus())
            );
        }
        return saved;
    }
}
