package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.Client;
import com.uade.mecanicosya.assistance.domain.ClientRepository;
import com.uade.mecanicosya.assistance.domain.Vehicle;
import com.uade.mecanicosya.assistance.domain.VehicleRepository;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ClientFacade {

    private final ClientRepository clientRepository;
    private final VehicleRepository vehicleRepository;

    public ClientFacade(ClientRepository clientRepository, VehicleRepository vehicleRepository) {
        this.clientRepository = clientRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional
    public Client register(String fullName, String phone, String email) {
        if (email != null && clientRepository.existsByEmail(email.trim().toLowerCase(Locale.ROOT))) {
            throw new DuplicateClientException(email.trim());
        }
        return clientRepository.save(new Client(UUID.randomUUID(), fullName, phone, email, Instant.now()));
    }

    @Transactional(readOnly = true)
    public Client find(UUID clientId) {
        return clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
    }

    @Transactional
    public Vehicle registerVehicle(
            UUID clientId,
            VehicleType type,
            String brand,
            String model,
            String color,
            String plate
    ) {
        Client client = find(clientId);
        return vehicleRepository.save(new Vehicle(UUID.randomUUID(), client, type, brand, model, color, plate));
    }

    @Transactional(readOnly = true)
    public List<Vehicle> vehicles(UUID clientId) {
        find(clientId);
        return vehicleRepository.findByClientId(clientId);
    }

    @Transactional(readOnly = true)
    public Vehicle findVehicle(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId).orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    }
}
