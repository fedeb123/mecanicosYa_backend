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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistance_status_changes")
public class StatusChange {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assistance_id", nullable = false)
    private AssistanceRequest assistance;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AssistanceStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssistanceStatus toStatus;

    @Column(nullable = false)
    private Instant changedAt;

    protected StatusChange() {
    }

    public StatusChange(
            UUID id,
            AssistanceRequest assistance,
            AssistanceStatus fromStatus,
            AssistanceStatus toStatus,
            Instant changedAt
    ) {
        this.id = id;
        this.assistance = assistance;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public AssistanceStatus getFromStatus() {
        return fromStatus;
    }

    public AssistanceStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
