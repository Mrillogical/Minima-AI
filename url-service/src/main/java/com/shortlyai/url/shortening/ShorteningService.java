package com.minima-AI.url.shortening;

import com.minima-AI.url.common.dto.ShortenRequest;
import com.minima-AI.url.common.dto.ShortenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ShorteningService {

    // Shorten a URL â€” saves to DB, caches in Redis, publishes Kafka event
    ShortenResponse shorten(ShortenRequest request, UUID userId);

    // Resolve a slug to its original URL â€” Redis first, Postgres fallback
    String resolve(String slug, HttpServletRequest request);

    // Soft-delete a URL â€” only the owner can delete their own URL
    void delete(Long id, UUID userId);

    // Get a specific URL of a user
    ShortenResponse getUrl(Long id, UUID userId);

    // Get all URLs of a user
    Page<ShortenResponse> getUserUrls(UUID userId, Pageable pageable);

    // Get URL based on slug
    ShortenResponse getUrlBySlug(String slug, UUID userId);

    // Delete URL by slug
    void deleteUrl(String slug, UUID userId);
}

