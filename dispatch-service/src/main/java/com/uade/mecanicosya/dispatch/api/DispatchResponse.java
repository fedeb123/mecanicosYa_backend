package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.domain.DispatchMatch;
import com.uade.mecanicosya.dispatch.domain.DispatchOffer;
import com.uade.mecanicosya.dispatch.domain.DispatchStatus;
import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.OfferStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispatchResponse(
        UUID assistanceId,
        UUID mechanicId,
        Double distanceKm,
        Integer etaMinutes,
        DispatchStatus status,
        int round,
        Instant offersExpireAt,
        List<OfferSummary> offers,
        Instant createdAt
) {

    public static DispatchResponse from(DispatchMatch match, List<DispatchOffer> offers) {
        return new DispatchResponse(
                match.getAssistanceId(),
                match.getMechanicId(),
                match.getDistanceKm(),
                match.getEtaMinutes(),
                match.getStatus(),
                match.getRound(),
                match.getOffersExpireAt(),
                offers.stream().map(OfferSummary::from).toList(),
                match.getCreatedAt()
        );
    }

    public record OfferSummary(
            UUID mechanicId,
            double distanceKm,
            Integer etaMinutes,
            EtaSource etaSource,
            int ranking,
            OfferStatus status
    ) {

        static OfferSummary from(DispatchOffer offer) {
            return new OfferSummary(offer.getMechanicId(), offer.getDistanceKm(), offer.getEtaMinutes(),
                    offer.getEtaSource(), offer.getRanking(), offer.getStatus());
        }
    }
}
