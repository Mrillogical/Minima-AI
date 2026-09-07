package com.minima-AI.url.dlq;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {

    // Retry job fetches all unprocessed rows under retry limit
    // MAX_RETRIES = 5 - after that, row stays, human must investigate
    @Query("""
            SELECT f FROM FailedEvent f
            WHERE f.processed = false
            AND f.retryCount < :maxRetries
            ORDER BY f.createdAt ASC
            """)
    Page<FailedEvent> findRetryable(@Param("maxRetries") int maxRetries, Pageable pageable);

    @Modifying
    @Query("UPDATE FailedEvent f SET f.processed = true WHERE f.id = :id")
    void markProcessed(@Param("id") Long id);
}

