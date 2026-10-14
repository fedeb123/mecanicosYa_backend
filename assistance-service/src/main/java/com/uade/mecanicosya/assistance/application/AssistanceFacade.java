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

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AssistanceFacade {

    private final AssistanceRepository repository;
    private final StatusChangeRepository statusChangeRepository;
    private final ClientFacade clientFacade;
    private final AssistanceFactory factory;
    private final ApplicationEventPublisher applicationEventPublisher;

    public AssistanceFacade(
            AssistanceRepository repository,
            StatusChangeRepository statusChangeRepository,
            ClientFacade clientFacade,
            AssistanceFactory factory,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.repository = repository;
        this.statusChangeRepository = statusChangeRepository;
        this.clientFacade = clientFacade;
        this.factory = factory;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public AssistanceRequest request(
            UUID clientId,
            UUID vehicleId,
            String description,
            String problemType,
            String requiredSkill,
            double latitude,
            double longitude
    ) {
        Client client = clientFacade.find(clientId);
        Vehicle vehicle = clientFacade.findVehicle(vehicleId);
        AssistanceRequest assistance = factory.create(
                client,
                vehicle,
                description,
                problemType,
                requiredSkill,
                latitude,
                longitude
        );
        AssistanceRequest saved = repository.save(assistance);
        applicationEventPublisher.publishEvent(
                AssistanceStatusChangedEvent.of(saved.getId(), null, saved.getStatus())
        );
        applicationEventPublisher.publishEvent(AssistanceRequestedEvent.from(saved));
        return saved;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyMatch(UUID assistanceId, UUID mechanicId) {
        transition(assistanceId, assistance -> assistance.assignMechanic(mechanicId));
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
