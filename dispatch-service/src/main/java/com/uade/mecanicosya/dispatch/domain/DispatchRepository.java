package com.uade.mecanicosya.dispatch.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DispatchRepository extends JpaRepository<DispatchMatch, UUID> {

    /**
     * Asigna el pedido sólo si sigue ofrecido, en la misma ronda y sin vencer. Es un UPDATE condicional: si dos
     * mecánicos aceptan a la vez, la base serializa las dos escrituras sobre la fila y sólo una cumple la condición.
     *
     * @return 1 si este mecánico ganó, 0 si el pedido ya no estaba disponible
     */
    default int claim(UUID assistanceId, int round, UUID mechanicId, double distanceKm, Integer etaMinutes, Instant now) {
        return claimIfOffered(assistanceId, round, mechanicId, distanceKm, etaMinutes, now, DispatchStatus.OFFERED,
                DispatchStatus.MATCHED);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DispatchMatch d
               set d.status = :matched,
                   d.mechanicId = :mechanicId,
                   d.distanceKm = :distanceKm,
                   d.etaMinutes = :etaMinutes,
                   d.version = d.version + 1
             where d.assistanceId = :assistanceId
               and d.round = :round
               and d.status = :offered
               and d.offersExpireAt > :now
            """)
    int claimIfOffered(
            @Param("assistanceId") UUID assistanceId,
            @Param("round") int round,
            @Param("mechanicId") UUID mechanicId,
            @Param("distanceKm") double distanceKm,
            @Param("etaMinutes") Integer etaMinutes,
            @Param("now") Instant now,
            @Param("offered") DispatchStatus offered,
            @Param("matched") DispatchStatus matched
    );

    List<DispatchMatch> findByStatusAndOffersExpireAtLessThanEqual(DispatchStatus status, Instant now);

    /**
     * ¿El mecánico tiene un trabajo aceptado y sin terminar? Un mecánico ocupado no recibe ofertas ni acepta otras.
     */
    @Query("""
            select count(d) > 0
              from DispatchMatch d
             where d.mechanicId = :mechanicId
               and d.status = com.uade.mecanicosya.dispatch.domain.DispatchStatus.MATCHED
               and d.completedAt is null
            """)
    boolean hasActiveJob(@Param("mechanicId") UUID mechanicId);

    /** De los mecánicos dados, los que están en un trabajo. */
    @Query("""
            select d.mechanicId
              from DispatchMatch d
             where d.mechanicId in :mechanicIds
               and d.status = com.uade.mecanicosya.dispatch.domain.DispatchStatus.MATCHED
               and d.completedAt is null
            """)
    List<UUID> findBusyMechanics(@Param("mechanicIds") Collection<UUID> mechanicIds);
}
