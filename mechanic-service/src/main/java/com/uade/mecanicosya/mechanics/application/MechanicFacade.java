package com.uade.mecanicosya.mechanics.application;

import com.uade.mecanicosya.mechanics.domain.Mechanic;
import com.uade.mecanicosya.mechanics.domain.MechanicFactory;
import com.uade.mecanicosya.mechanics.domain.MechanicRepository;
import com.uade.mecanicosya.mechanics.domain.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MechanicFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(MechanicFacade.class);

    private final MechanicRepository repository;
    private final MechanicFactory factory;

    public MechanicFacade(MechanicRepository repository, MechanicFactory factory) {
        this.repository = repository;
        this.factory = factory;
    }

    @Transactional
    public Mechanic register(
            String name,
            String email,
            double latitude,
            double longitude,
            Set<String> skills,
            Set<VehicleType> vehicleTypes
    ) {
        return repository.save(factory.create(name, email, latitude, longitude, skills, vehicleTypes));
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

    @Transactional(readOnly = true)
    public List<Mechanic> findCandidates(String skill, VehicleType vehicleType) {
        List<Mechanic> candidates = vehicleType == null
                ? repository.findAvailableBySkill(skill)
                : repository.findAvailableBySkillAndVehicleType(skill, vehicleType);
        LOGGER.info("Found {} available mechanics for skill {} and vehicle {}", candidates.size(), skill, vehicleType);
        return candidates;
    }
}

