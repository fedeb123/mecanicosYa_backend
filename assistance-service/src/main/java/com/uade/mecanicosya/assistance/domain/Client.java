package com.uade.mecanicosya.assistance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false)
    private Instant createdAt;

    protected Client() {
    }

    public Client(UUID id, String fullName, String phone, String email, Instant createdAt) {
        this.id = id;
        this.fullName = requireText(fullName, "Full name");
        this.phone = requireText(phone, "Phone");
        this.email = requireText(email, "Email").toLowerCase(Locale.ROOT);
        this.createdAt = createdAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
