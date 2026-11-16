package com.uade.mecanicosya.assistance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssistanceRepository extends JpaRepository<AssistanceRequest, UUID> {

    Optional<AssistanceRequest> findByClient_IdAndIdempotencyKey(UUID clientId, String idempotencyKey);

    List<AssistanceRequest> findByClient_IdOrderByCreatedAtDesc(UUID clientId);

    List<AssistanceRequest> findByAssignedMechanicIdOrderByCreatedAtDesc(UUID mechanicId);

    List<AssistanceRequest> findByStatusAndOffersExpireAtLessThanEqual(AssistanceStatus status, Instant now);
}
