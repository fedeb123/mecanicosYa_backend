package com.uade.mecanicosya.dispatch.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DispatchRepository extends JpaRepository<DispatchMatch, UUID> {
}

