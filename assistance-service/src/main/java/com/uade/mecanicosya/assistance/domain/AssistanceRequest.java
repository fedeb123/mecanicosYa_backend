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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "assistance_requests")
public class AssistanceRequest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 80)
    private String problemType;

    @Column(nullable = false, length = 80)
    private String requiredSkill;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssistanceStatus status;

    private UUID assignedMechanicId;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected AssistanceRequest() {
    }

    public AssistanceRequest(
            UUID id,
            Client client,
            Vehicle vehicle,
            String description,
            String problemType,
            String requiredSkill,
            double latitude,
            double longitude,
            Instant createdAt
    ) {
        if (client == null || vehicle == null) {
            throw new IllegalArgumentException("Client and vehicle are required");
        }
        if (!vehicle.belongsTo(client.getId())) {
            throw new IllegalArgumentException("The vehicle does not belong to the client");
        }
        this.id = id;
        this.client = client;
        this.vehicle = vehicle;
        this.vehicleType = vehicle.getType();
        this.description = description;
        this.problemType = normalize(problemType);
        this.requiredSkill = normalize(requiredSkill);
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = AssistanceStatus.REQUESTED;
        this.createdAt = createdAt;
    }

    public void assignMechanic(UUID mechanicId) {
        requireStatus("receive a mechanic", AssistanceStatus.REQUESTED, AssistanceStatus.MATCHED);
        this.assignedMechanicId = mechanicId;
        this.status = AssistanceStatus.MATCHED;
    }

    public void start() {
        requireStatus("start", AssistanceStatus.MATCHED);
        this.status = AssistanceStatus.IN_PROGRESS;
    }

    public void complete() {
        requireStatus("complete", AssistanceStatus.IN_PROGRESS);
        this.status = AssistanceStatus.COMPLETED;
    }

    public void cancel() {
        requireStatus(
                "be cancelled",
                AssistanceStatus.REQUESTED,
                AssistanceStatus.MATCHED,
                AssistanceStatus.IN_PROGRESS
        );
        this.status = AssistanceStatus.CANCELLED;
    }

    private void requireStatus(String action, AssistanceStatus... allowed) {
        for (AssistanceStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new InvalidStatusTransitionException(status, action);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value cannot be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return client.getId();
    }

    public UUID getVehicleId() {
        return vehicle.getId();
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public String getDescription() {
        return description;
    }

    public String getProblemType() {
        return problemType;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public AssistanceStatus getStatus() {
        return status;
    }

    public UUID getAssignedMechanicId() {
        return assignedMechanicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
