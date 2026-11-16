package com.uade.mecanicosya.dispatch.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchOfferRepository extends JpaRepository<DispatchOffer, UUID> {

    Optional<DispatchOffer> findByAssistanceIdAndRoundAndMechanicId(UUID assistanceId, int round, UUID mechanicId);

    List<DispatchOffer> findByAssistanceIdAndRoundOrderByRankingAsc(UUID assistanceId, int round);

    @Query("""
            select new com.uade.mecanicosya.dispatch.domain.OpenOffer(
                   d.assistanceId, o.mechanicId, d.requiredSkill, d.vehicleType, d.latitude, d.longitude,
                   o.distanceKm, o.ranking, d.offersExpireAt)
              from DispatchOffer o, DispatchMatch d
             where d.assistanceId = o.assistanceId
               and d.round = o.round
               and o.mechanicId = :mechanicId
               and o.status = com.uade.mecanicosya.dispatch.domain.OfferStatus.PENDING
               and d.status = com.uade.mecanicosya.dispatch.domain.DispatchStatus.OFFERED
               and d.offersExpireAt > :now
             order by d.offersExpireAt asc
            """)
    List<OpenOffer> findOpenOffers(@Param("mechanicId") UUID mechanicId, @Param("now") Instant now);

    /**
     * Cierra las ofertas que siguen pendientes en una ronda (porque otro aceptó, venció o se canceló).
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update DispatchOffer o
               set o.status = :status
             where o.assistanceId = :assistanceId
               and o.round = :round
               and o.status = com.uade.mecanicosya.dispatch.domain.OfferStatus.PENDING
            """)
    int closePending(
            @Param("assistanceId") UUID assistanceId,
            @Param("round") int round,
            @Param("status") OfferStatus status
    );
}
