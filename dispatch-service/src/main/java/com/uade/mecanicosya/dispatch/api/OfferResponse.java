package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.OpenOffer;

import java.time.Instant;
import java.util.UUID;

public record OfferResponse(
        UUID assistanceId,
        UUID mechanicId,
        String requiredSkill,
        String vehicleType,
        Double latitude,
        Double longitude,
        double distanceKm,
        Integer etaMinutes,
        EtaSource etaSource,
        int ranking,
        Instant expiresAt
) {

    public static OfferResponse from(OpenOffer offer) {
        return new OfferResponse(
                offer.assistanceId(),
                offer.mechanicId(),
                offer.requiredSkill(),
                offer.vehicleType(),
                offer.latitude(),
                offer.longitude(),
                offer.distanceKm(),
                offer.etaMinutes(),
                offer.etaSource(),
                offer.ranking(),
                offer.expiresAt()
        );
    }
}
