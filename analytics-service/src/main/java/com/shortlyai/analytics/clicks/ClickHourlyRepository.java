package com.minima-AI.analytics.clicks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickHourlyRepository extends JpaRepository<ClickHourly, Long> {

    List<ClickHourly> findByUrlIdInAndHour(List<Long> urlIds, Instant hour);

    // Called from ClickServiceImpl.deleteClickData() so hourly rollups don't
    // orphan forever when the URL itself is deleted.
    @Modifying
    void deleteByUrlId(Long urlId);

    // All hourly buckets for a URL from a point in time forward
    @Query("""
            SELECT c FROM ClickHourly c
            WHERE c.urlId = :urlId
            AND c.hour >= :since
            ORDER BY c.hour ASC
            """)
    List<ClickHourly> findByUrlIdAndSince(
            @Param("urlId") Long urlId,
            @Param("since") Instant since
    );
}

