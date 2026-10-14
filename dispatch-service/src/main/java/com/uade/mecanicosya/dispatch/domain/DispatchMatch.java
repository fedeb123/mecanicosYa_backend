package com.uade.mecanicosya.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatch_matches")
public class DispatchMatch {

    @Id
    private UUID assistanceId;

    private UUID mechanicId;

    private Double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DispatchStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected DispatchMatch() {
    }

    private DispatchMatch(
            UUID assistanceId,
            UUID mechanicId,
            Double distanceKm,
            DispatchStatus status,
            Instant createdAt
    ) {
        this.assistanceId = assistanceId;
        this.mechanicId = mechanicId;
        this.distanceKm = distanceKm;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static DispatchMatch matched(
            UUID assistanceId,
            UUID mechanicId,
            double distanceKm
    ) {
        return new DispatchMatch(
                assistanceId,
                mechanicId,
                distanceKm,
                DispatchStatus.MATCHED,
                Instant.now()
        );
    }

    public static DispatchMatch noCandidate(UUID assistanceId) {
        return new DispatchMatch(
                assistanceId,
                null,
                null,
                DispatchStatus.NO_CANDIDATE,
                Instant.now()
        );
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

    public DispatchStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
