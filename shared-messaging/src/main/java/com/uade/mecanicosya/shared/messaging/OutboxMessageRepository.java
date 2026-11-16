package com.uade.mecanicosya.shared.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop100ByPublishedAtIsNullOrderByIdAsc();

    List<OutboxMessage> findByTopicOrderByIdAsc(String topic);

    long countByPublishedAtIsNull();

    @Modifying
    @Query("update OutboxMessage m set m.publishedAt = :publishedAt where m.id = :id")
    int markPublished(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);
}
