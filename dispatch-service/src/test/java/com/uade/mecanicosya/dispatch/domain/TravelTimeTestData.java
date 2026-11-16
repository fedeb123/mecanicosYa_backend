package com.uade.mecanicosya.dispatch.domain;

import java.util.Set;
import java.util.UUID;

final class TravelTimeTestData {

    private TravelTimeTestData() {
    }

    static MechanicCandidate mechanic(String name, double latitude, double longitude, double rating) {
        return new MechanicCandidate(UUID.randomUUID(), name, name + "@example.com", latitude, longitude, true, rating,
                Set.of("CHAIN_REPAIR"));
    }
}
