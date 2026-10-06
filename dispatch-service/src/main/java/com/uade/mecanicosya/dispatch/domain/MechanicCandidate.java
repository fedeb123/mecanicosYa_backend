package com.uade.mecanicosya.dispatch.domain;

import java.util.Set;
import java.util.UUID;

public record MechanicCandidate(
        UUID id,
        String name,
        String email,
        double latitude,
        double longitude,
        boolean available,
        double rating,
        Set<String> skills
) {
}

