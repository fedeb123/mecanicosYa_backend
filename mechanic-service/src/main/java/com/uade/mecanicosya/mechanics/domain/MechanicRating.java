package com.uade.mecanicosya.mechanics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Una calificación recibida por un mecánico. La clave es la asistencia: cada asistencia
 * califica una sola vez, así que reintentar el mismo pedido no cambia el promedio.
 */
@Entity
@Table(name = "mechanic_ratings")
public class MechanicRating {

    @Id
    private UUID assistanceId;

    @Column(nullable = false)
    private UUID mechanicId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private Instant createdAt;

    protected MechanicRating() {
    }

    public MechanicRating(UUID assistanceId, UUID mechanicId, int score, Instant createdAt) {
        this.assistanceId = assistanceId;
        this.mechanicId = mechanicId;
        this.score = score;
        this.createdAt = createdAt;
    }

    public UUID getAssistanceId() {
        return assistanceId;
    }

    public UUID getMechanicId() {
        return mechanicId;
    }

    public int getScore() {
        return score;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
