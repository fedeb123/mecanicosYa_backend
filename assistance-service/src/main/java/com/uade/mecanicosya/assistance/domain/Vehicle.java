package com.uade.mecanicosya.assistance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleType type;

    @Column(nullable = false, length = 60)
    private String brand;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(length = 30)
    private String color;

    @Column(unique = true, length = 10)
    private String plate;

    protected Vehicle() {
    }

    public Vehicle(
            UUID id,
            Client client,
            VehicleType type,
            String brand,
            String model,
            String color,
            String plate
    ) {
        if (client == null || type == null) {
            throw new IllegalArgumentException("Client and vehicle type are required");
        }
        this.id = id;
        this.client = client;
        this.type = type;
        this.brand = requireText(brand, "Brand");
        this.model = requireText(model, "Model");
        this.color = blankToNull(color);
        this.plate = normalizePlate(type, plate);
    }

    private static String normalizePlate(VehicleType type, String plate) {
        String normalized = blankToNull(plate);
        if (normalized == null) {
            if (type.requiresPlate()) {
                throw new IllegalArgumentException("A " + type + " requires a plate");
            }
            return null;
        }
        return normalized.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean belongsTo(UUID clientId) {
        return client.getId().equals(clientId);
    }

    public UUID getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public VehicleType getType() {
        return type;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public String getColor() {
        return color;
    }

    public String getPlate() {
        return plate;
    }
}
