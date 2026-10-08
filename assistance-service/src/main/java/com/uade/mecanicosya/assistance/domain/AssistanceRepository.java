package com.uade.mecanicosya.assistance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssistanceRepository extends JpaRepository<AssistanceRequest, UUID> {
}

