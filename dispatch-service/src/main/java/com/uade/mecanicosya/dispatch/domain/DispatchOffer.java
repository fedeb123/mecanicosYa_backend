package com.uade.mecanicosya.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Oferta de un pedido a un mecánico en una ronda. El mecánico la acepta o la rechaza.
 */
@Entity
@Table(
        name = "dispatch_offers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assistance_id", "round", "mechanic_id"})
)
public class DispatchOffer {

    @Id
    private UUID id;

    @Column(name = "assistance_id", nullable = false)
    private UUID assistanceId;

    @Column(nullable = false)
    private int round;

    @Column(name = "mechanic_id", nullable = false)
    private UUID mechanicId;

    @Column(nullable = false)
    private double distanceKm;

    /** Posición en el orden de la estrategia: 1 es el más conveniente. */
    @Column(nullable = false)
    private int ranking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant respondedAt;

    /** Minutos que tarda el mecánico en llegar al pedido. */
    private Integer etaMinutes;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private EtaSource etaSource;

    protected DispatchOffer() {
    }

    public DispatchOffer(
            UUID assistanceId,
            int round,
            UUID mechanicId,
            double distanceKm,
            int ranking,
            Instant createdAt
    ) {
        this(assistanceId, round, mechanicId, distanceKm, ranking, createdAt, null, null);
    }

    public DispatchOffer(
            UUID assistanceId,
            int round,
            UUID mechanicId,
            double distanceKm,
            int ranking,
            Instant createdAt,
            Integer etaMinutes,
            EtaSource etaSource
    ) {
        this.etaMinutes = etaMinutes;
        this.etaSource = etaSource;
        this.id = UUID.randomUUID();
        this.assistanceId = assistanceId;
        this.round = round;
        this.mechanicId = mechanicId;
        this.distanceKm = distanceKm;
        this.ranking = ranking;
        this.status = OfferStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void accept(Instant now) {
        this.status = OfferStatus.ACCEPTED;
        this.respondedAt = now;
    }

    /**
     * Rechazar es idempotente: una oferta que ya no está pendiente queda como estaba.
     */
    public void decline(Instant now) {
        if (status == OfferStatus.PENDING) {
            this.status = OfferStatus.DECLINED;
            this.respondedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssistanceId() {
        return assistanceId;
    }

    public int getRound() {
        return round;
    }

    public UUID getMechanicId() {
        return mechanicId;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public int getRanking() {
        return ranking;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public EtaSource getEtaSource() {
        return etaSource;
    }
}
