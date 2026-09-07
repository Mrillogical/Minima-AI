package com.minima-AI.analytics.clicks;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    // Count total clicks for a URL â€” fallback when Redis misses
    long countByUrlId(Long urlId);

    @Query("""
            SELECT new com.minima-AI.analytics.clicks.TopUrlResponse(c.urlId, COUNT(c))
            FROM ClickEvent c
            WHERE c.clickedAt >= :from AND c.clickedAt < :to
            GROUP BY c.urlId
            """)
    List<TopUrlResponse> countGroupedByUrlIdBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Bulk delete all clicks for a URL â€” called on url.deleted event
    // urlId (immutable PK) not slug (reusable) â€” see earlier fix
    @Modifying
    void deleteByUrlId(Long urlId);

    // Top N URLs by total click count â€” groups all click_events by urlId
    @Query("""
        SELECT new com.minima-AI.analytics.clicks.TopUrlResponse(c.urlId, COUNT(c))
        FROM ClickEvent c
        WHERE c.userId = :userId
        GROUP BY c.urlId
        ORDER BY COUNT(c) DESC
        """)
    List<TopUrlResponse> findTopUrlsByUserId(@Param("userId") UUID userId, Pageable pageable);

    boolean existsByUrlIdAndUserId(Long urlId, UUID userId);
}

