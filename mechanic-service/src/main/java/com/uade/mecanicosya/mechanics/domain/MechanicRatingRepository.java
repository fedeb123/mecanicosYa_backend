package com.uade.mecanicosya.mechanics.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MechanicRatingRepository extends JpaRepository<MechanicRating, UUID> {
}
