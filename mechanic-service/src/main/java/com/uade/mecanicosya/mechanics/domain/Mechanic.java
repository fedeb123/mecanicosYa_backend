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

    /** Para que el repartidor que tiene asignado lo pueda llamar. Opcional: los perfiles viejos no lo tienen. */
    @Column(length = 40)
    private String phone;

    /** Las filas viejas no tienen el dato: se leen como mecánico móvil. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MechanicKind kind;

    /** Dirección del taller, para mostrarle al repartidor adónde ir. Null para un mecánico móvil. */
    @Column(length = 200)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private boolean available;

    @Column(nullable = false)
    private double rating;

    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int ratingCount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mechanic_skills", joinColumns = @JoinColumn(name = "mechanic_id"))
    @Column(name = "skill", nullable = false, length = 80)
    private Set<String> skills = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mechanic_vehicle_types", joinColumns = @JoinColumn(name = "mechanic_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private Set<VehicleType> vehicleTypes = EnumSet.noneOf(VehicleType.class);

    // El default llena las filas que ya existían cuando se agregó la columna.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'PENDING_VERIFICATION' not null")
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_VERIFICATION;

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
        this.verificationStatus = VerificationStatus.PENDING_VERIFICATION;
    }

    public void changePhone(String phone) {
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
    }

    /** Un taller guarda su dirección; un mecánico móvil no tiene. */
    public void changeKind(MechanicKind kind, String address) {
        this.kind = kind == null ? MechanicKind.MOBILE : kind;
        this.address = this.kind == MechanicKind.WORKSHOP && address != null && !address.isBlank()
                ? address.trim()
                : null;
    }

    public MechanicKind getKind() {
        return kind == null ? MechanicKind.MOBILE : kind;
    }

    public String getAddress() {
        return address;
    }

    public boolean isWorkshop() {
        return getKind() == MechanicKind.WORKSHOP;
    }

    /** Ponerse disponible exige estar aprobado; dejar de estarlo, no. */
    public void changeAvailability(boolean available, double latitude, double longitude) {
        if (available && !isApproved()) {
            throw new MechanicNotApprovedException(id);
        }
        this.available = available;
        // Un taller no se mueve: su ubicación es la dirección que cargó al registrarse.
        if (!isWorkshop()) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * Suma una calificación de 1 a 5 al promedio. Mientras no tenga calificaciones, el mecánico
     * muestra 5.0; la primera calificación reemplaza ese valor inicial.
     */
    public void addRating(int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("The rating must be between 1 and 5");
        }
        this.rating = (rating * ratingCount + score) / (ratingCount + 1);
        this.ratingCount++;
    }

    public void approve() {
        this.verificationStatus = VerificationStatus.APPROVED;
    }

    public boolean isApproved() {
        return verificationStatus == VerificationStatus.APPROVED;
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

    public String getPhone() {
        return phone;
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

    public int getRatingCount() {
        return ratingCount;
    }

    public Set<String> getSkills() {
        return Collections.unmodifiableSet(skills);
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public Set<VehicleType> getVehicleTypes() {
        return Collections.unmodifiableSet(vehicleTypes);
    }
}

