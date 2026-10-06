package com.uade.mecanicosya.mechanics.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "mechanics")
public class Mechanic {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private boolean available;

    @Column(nullable = false)
    private double rating;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mechanic_skills", joinColumns = @JoinColumn(name = "mechanic_id"))
    @Column(name = "skill", nullable = false, length = 80)
    private Set<String> skills = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mechanic_vehicle_types", joinColumns = @JoinColumn(name = "mechanic_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private Set<VehicleType> vehicleTypes = EnumSet.noneOf(VehicleType.class);

    @Version
    private long version;

    protected Mechanic() {
    }

    public Mechanic(
            UUID id,
            String name,
            String email,
            double latitude,
            double longitude,
            Set<String> skills,
            Set<VehicleType> vehicleTypes
    ) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.latitude = latitude;
        this.longitude = longitude;
        this.skills = normalizeSkills(skills);
        this.vehicleTypes = normalizeVehicleTypes(vehicleTypes);
        this.available = false;
        this.rating = 5.0;
    }

    public void changeAvailability(boolean available, double latitude, double longitude) {
        this.available = available;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean hasSkill(String requiredSkill) {
        return skills.contains(normalizeSkill(requiredSkill));
    }

    public boolean serves(VehicleType vehicleType) {
        return vehicleTypes.contains(vehicleType);
    }

    private static Set<VehicleType> normalizeVehicleTypes(Set<VehicleType> values) {
        if (values == null || values.isEmpty()) {
            return EnumSet.of(VehicleType.BICYCLE);
        }
        return EnumSet.copyOf(values);
    }

    private static Set<String> normalizeSkills(Set<String> values) {
        Set<String> normalized = new HashSet<>();
        values.forEach(value -> normalized.add(normalizeSkill(value)));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A mechanic must declare at least one skill");
        }
        return normalized;
    }

    private static String normalizeSkill(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Skill cannot be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean isAvailable() {
        return available;
    }

    public double getRating() {
        return rating;
    }

    public Set<String> getSkills() {
        return Collections.unmodifiableSet(skills);
    }

    public Set<VehicleType> getVehicleTypes() {
        return Collections.unmodifiableSet(vehicleTypes);
    }
}

