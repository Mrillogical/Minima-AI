package com.minima-AI.url.shortening;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    // Redirect lookup â€” hottest query, hits idx_urls_slug index
    // Optional forces caller to handle "slug not found" explicitly
    Optional<Url> findBySlugAndIsActiveTrueAndExpiresAtAfter(String slug, Instant now);

    // Check slug availability before saving a custom alias
    boolean existsBySlug(String slug);

    // "My URLs" page â€” all active URLs for a user, newest first
    Page<Url> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Soft delete â€” sets isActive=false, never removes the row
    // @Modifying â€” required for any UPDATE/DELETE query
    @Modifying
    @Query(""" 
               UPDATE Url u
               SET u.isActive = false,
               u.updatedAt = :now
               WHERE u.id = :id
               AND u.userId = :userId
    """)
    int softDeleteByIdAndUserId(@Param("id") Long id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query(value = """
               UPDATE urls u
               SET click_count = u.click_count + v.cnt
               FROM (SELECT unnest(:ids) AS id, unnest(:counts) AS cnt) v
               WHERE u.id = v.id
    """, nativeQuery = true)
    void batchIncrementClickCounts(@Param("ids") Long[] ids, @Param("counts") Long[] counts);

    // For loading only the user's URL's. Prevents loading other user's URLs.
    Optional<Url> findByIdAndUserIdAndIsActiveTrue(Long id, UUID userId);

    @Modifying
    @Query("""
              UPDATE Url u
              SET u.isActive = false,
              u.updatedAt = :now WHERE
              u.expiresAt < :now
              AND u.isActive = true
    """)
    int deactivateExpiredUrls(@Param("now") Instant now);

    // For cache warming job
    Page<Url> findByIsActiveTrueOrderByClickCountDesc(Pageable pageable);

    // Fetch only slugs of expired URLs â€” no full entity load
    // Spring Data projection: interface with getter = SELECT slug only
    @Query("""
        SELECT u.slug FROM Url u
        WHERE u.expiresAt < :now
        AND u.isActive = true
        """)
    List<String> findExpiredSlugs(@Param("now") Instant now);


    Optional<Url> findBySlugAndUserIdAndIsActiveTrue(String slug, UUID userId);

    // Soft delete by slug â€” mirrors softDeleteByIdAndUserId.
    // Keeps both delete paths (by id, by slug) consistent: neither
    // hard-deletes, so analytics/click-history FKs never dangle.
    @Modifying
    @Query("""
               UPDATE Url u
               SET u.isActive = false,
               u.updatedAt = :now
               WHERE u.slug = :slug
               AND u.userId = :userId
    """)
    int softDeleteBySlugAndUserId(@Param("slug") String slug, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE Url u
        SET u.title = :title,
            u.category = :category,
            u.isSafe = :isSafe,
            u.updatedAt = CURRENT_TIMESTAMP
        WHERE u.id = :urlId
        """)
    void updateClassification(
            @Param("urlId") Long urlId,
            @Param("title") String title,
            @Param("category") String category,
            @Param("isSafe") boolean isSafe
    );
}

