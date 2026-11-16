package com.uade.mecanicosya.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Despacho de una asistencia. Cada ronda ofrece el pedido a varios mecánicos y el primero que acepta se lo queda.
 */
@Entity
@Table(name = "dispatch_matches")
public class DispatchMatch {

    @Id
    private UUID assistanceId;

    private UUID mechanicId;

    private Double distanceKm;

    /** Minutos que tarda en llegar el mecánico que tomó el pedido. */
    private Integer etaMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DispatchStatus status;

    @Column(length = 80)
    private String requiredSkill;

    @Column(length = 20)
    private String vehicleType;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false, columnDefinition = "integer default 1 not null")
    private int round;

    private Instant offersExpireAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0 not null")
    private long version;

    protected DispatchMatch() {
    }

    private DispatchMatch(UUID assistanceId, DispatchStatus status, Instant createdAt) {
        this.assistanceId = assistanceId;
        this.status = status;
        this.round = 1;
        this.createdAt = createdAt;
    }

    public static DispatchMatch offered(
            UUID assistanceId,
            String requiredSkill,
            String vehicleType,
            double latitude,
            double longitude,
            Instant now,
            Instant offersExpireAt
    ) {
        DispatchMatch match = new DispatchMatch(assistanceId, DispatchStatus.OFFERED, now);
        match.describe(requiredSkill, vehicleType, latitude, longitude);
        match.offersExpireAt = offersExpireAt;
        return match;
    }

    public static DispatchMatch noCandidate(
            UUID assistanceId,
            String requiredSkill,
            String vehicleType,
            double latitude,
            double longitude,
            Instant now
    ) {
        DispatchMatch match = new DispatchMatch(assistanceId, DispatchStatus.NO_CANDIDATE, now);
        match.describe(requiredSkill, vehicleType, latitude, longitude);
        return match;
    }

    /**
     * Abre una ronda nueva sobre un pedido que quedó sin mecánico. Devuelve el número de la ronda.
     */
    public int reopen(Instant offersExpireAt) {
        requireStatus("be offered again", DispatchStatus.UNMATCHED, DispatchStatus.NO_CANDIDATE, DispatchStatus.OFFERED);
        this.round++;
        this.status = DispatchStatus.OFFERED;
        this.offersExpireAt = offersExpireAt;
        return round;
    }

    public void markNoCandidate() {
        this.status = DispatchStatus.NO_CANDIDATE;
        this.offersExpireAt = null;
    }

    public void expire() {
        requireStatus("expire", DispatchStatus.OFFERED);
        this.status = DispatchStatus.UNMATCHED;
    }

    public void cancel() {
        this.status = DispatchStatus.CANCELLED;
    }

    public boolean isOpen(Instant now) {
        return status == DispatchStatus.OFFERED && offersExpireAt != null && offersExpireAt.isAfter(now);
    }

    /**
     * Un pedido que no está tomado, cancelado ni abierto puede volver a ofrecerse.
     */
    public boolean canBeOfferedAgain(Instant now) {
        return status == DispatchStatus.UNMATCHED
                || status == DispatchStatus.NO_CANDIDATE
                || (status == DispatchStatus.OFFERED && !isOpen(now));
    }

    public boolean isTakenBy(UUID candidateMechanicId) {
        return status == DispatchStatus.MATCHED && candidateMechanicId.equals(mechanicId);
    }

    private void describe(String requiredSkill, String vehicleType, double latitude, double longitude) {
        this.requiredSkill = requiredSkill;
        this.vehicleType = vehicleType;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private void requireStatus(String action, DispatchStatus... allowed) {
        for (DispatchStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("A dispatch in status " + status + " cannot " + action);
    }

    public UUID getAssistanceId() {
        return assistanceId;
    }

    public UUID getMechanicId() {
        return mechanicId;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public int getRound() {
        return round;
    }

    public Instant getOffersExpireAt() {
        return offersExpireAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
