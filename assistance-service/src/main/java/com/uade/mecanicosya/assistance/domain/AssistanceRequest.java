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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "assistance_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_assistance_client_idempotency_key",
                columnNames = {"client_id", "idempotency_key"}
        )
)
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

    /** Qué eligió el repartidor: MOBILE (que vaya un mecánico) o WORKSHOP (ir a un taller); null = cualquiera. */
    @Column(length = 20)
    private String preferredMechanicKind;

    /** Minutos estimados de llegada del mecánico asignado, según dispatch-service (null si no los calculó). */
    private Integer assignedEtaMinutes;

    /** Distancia por calle (o en línea recta, si la API de rutas no respondió) del mecánico asignado, en km. */
    private Double assignedDistanceKm;

    private Integer rating;

    @Column(length = 500)
    private String ratingComment;

    private Instant ratedAt;

    @Column(nullable = false)
    private Instant createdAt;

    /** Clave que manda la app para que un reintento no cree un pedido duplicado. */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    /** Hora del teléfono al crear el pedido; puede ser anterior a createdAt si estuvo en cola sin conexión. */
    private Instant clientCreatedAt;

    /** Hasta cuándo los mecánicos pueden aceptar la ronda de ofertas actual. */
    private Instant offersExpireAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TriageSource triageSource;

    private Double triageConfidence;

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
        this(id, client, vehicle, description, problemType, requiredSkill, latitude, longitude, createdAt, null, null);
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
            Instant createdAt,
            String idempotencyKey,
            Instant clientCreatedAt
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
        this.idempotencyKey = idempotencyKey;
        this.clientCreatedAt = clientCreatedAt;
    }

    /**
     * dispatch-service ofreció el pedido; queda publicado hasta {@code expiresAt}.
     */
    public void awaitOffers(Instant expiresAt) {
        requireStatus("wait for offers", AssistanceStatus.REQUESTED);
        this.offersExpireAt = expiresAt;
    }

    /**
     * Un mecánico tomó el pedido. Repetir la asignación del mismo mecánico no cambia nada; otro mecánico es un
     * conflicto.
     */
    public void assignMechanic(UUID mechanicId) {
        assignMechanic(mechanicId, null, null);
    }

    /** Igual que {@link #assignMechanic(UUID)}, guardando además cuánto tarda y a qué distancia está el mecánico. */
    public void assignMechanic(UUID mechanicId, Integer etaMinutes, Double distanceKm) {
        if (status == AssistanceStatus.MATCHED && mechanicId.equals(assignedMechanicId)) {
            // dispatch-service avisa primero por REST (sin ETA) y después por Kafka (con ETA): se completa lo que falte.
            if (assignedEtaMinutes == null) this.assignedEtaMinutes = etaMinutes;
            if (assignedDistanceKm == null) this.assignedDistanceKm = distanceKm;
            return;
        }
        requireStatus("receive a mechanic", AssistanceStatus.REQUESTED);
        this.assignedMechanicId = mechanicId;
        this.assignedEtaMinutes = etaMinutes;
        this.assignedDistanceKm = distanceKm;
        this.offersExpireAt = null;
        this.status = AssistanceStatus.MATCHED;
    }

    public void markUnmatched() {
        requireStatus("be left without a mechanic", AssistanceStatus.REQUESTED);
        this.offersExpireAt = null;
        this.status = AssistanceStatus.UNMATCHED;
    }

    public void republish() {
        requireStatus("be published again", AssistanceStatus.UNMATCHED);
        this.status = AssistanceStatus.REQUESTED;
    }

    public void start() {
        requireStatus("start", AssistanceStatus.MATCHED);
        this.status = AssistanceStatus.IN_PROGRESS;
    }

    public void complete() {
        requireStatus("complete", AssistanceStatus.IN_PROGRESS);
        this.status = AssistanceStatus.COMPLETED;
    }

    /**
     * Cancelar dos veces no es un error: la app puede reintentar la cancelación.
     */
    public void cancel() {
        if (status == AssistanceStatus.CANCELLED) {
            return;
        }
        requireStatus(
                "be cancelled",
                AssistanceStatus.REQUESTED,
                AssistanceStatus.UNMATCHED,
                AssistanceStatus.MATCHED,
                AssistanceStatus.IN_PROGRESS
        );
        this.offersExpireAt = null;
        this.status = AssistanceStatus.CANCELLED;
    }

    public void rate(int score, String comment, Instant ratedAt) {
        requireStatus("be rated", AssistanceStatus.COMPLETED);
        if (rating != null) {
            throw new AssistanceAlreadyRatedException(id);
        }
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("The rating must be between 1 and 5");
        }
        this.rating = score;
        this.ratingComment = comment == null || comment.isBlank() ? null : comment.trim();
        this.ratedAt = ratedAt;
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

    public void preferMechanicKind(String kind) {
        this.preferredMechanicKind = "MOBILE".equals(kind) || "WORKSHOP".equals(kind) ? kind : null;
    }

    public String getPreferredMechanicKind() {
        return preferredMechanicKind;
    }

    public UUID getAssignedMechanicId() {
        return assignedMechanicId;
    }

    public Integer getAssignedEtaMinutes() {
        return assignedEtaMinutes;
    }

    public Double getAssignedDistanceKm() {
        return assignedDistanceKm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Integer getRating() {
        return rating;
    }

    public String getRatingComment() {
        return ratingComment;
    }

    public Instant getRatedAt() {
        return ratedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getClientCreatedAt() {
        return clientCreatedAt;
    }

    public Instant getOffersExpireAt() {
        return offersExpireAt;
    }

    /** Guarda la clasificación del texto del pedido (urgencia, quién la hizo y con qué confianza). */
    public void recordTriage(Urgency urgency, TriageSource source, double confidence) {
        if (urgency == null || source == null) {
            throw new IllegalArgumentException("Urgency and source are required");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("The confidence must be between 0 and 1");
        }
        this.urgency = urgency;
        this.triageSource = source;
        this.triageConfidence = confidence;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public TriageSource getTriageSource() {
        return triageSource;
    }

    public Double getTriageConfidence() {
        return triageConfidence;
    }
}
