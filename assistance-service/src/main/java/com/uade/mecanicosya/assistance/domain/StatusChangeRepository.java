package com.uade.mecanicosya.assistance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatusChangeRepository extends JpaRepository<StatusChange, UUID> {

    List<StatusChange> findByAssistanceIdOrderByChangedAtAsc(UUID assistanceId);
}
