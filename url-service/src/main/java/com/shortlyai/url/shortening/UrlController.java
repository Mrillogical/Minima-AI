package com.minima-AI.url.shortening;

import com.minima-AI.url.common.dto.ShortenRequest;
import com.minima-AI.url.common.dto.ShortenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/urls")
@Slf4j
@Tag(name = "URL Controller")
public class UrlController {

    private final ShorteningService shorteningService;

    @Operation(
            summary = "Shorten URL",
            description = "Allows users to shorten an URL"
    )
    @PostMapping
    public ResponseEntity<ShortenResponse> shortenURL(
            @Valid @RequestBody ShortenRequest request,
            @AuthenticationPrincipal UUID userId
    ) {

        log.info("Create URL request from userId: {}", userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        shorteningService.shorten(
                                request, userId)
                );
    }

    @Operation(
            summary = "Get URLs",
            description = "Retrieves all URLs of an user"
    )
    @GetMapping
    public ResponseEntity<Page<ShortenResponse>> getAllUrls(
            @AuthenticationPrincipal UUID userId,
            Pageable pageable
    ) {

        log.info("Get URLs for userId: {}", userId);

        return ResponseEntity
                .ok(
                        shorteningService.getUserUrls(
                                userId,
                                pageable
                        )
                );
    }

    @Operation(
            summary = "Fetch URL",
            description = "Fetches an URL by its ID"
    )
    @GetMapping("/id/{id}")
    public ResponseEntity<ShortenResponse> getUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal UUID userId
    ) {

        log.info("Get URL id: {} for userId: {}", id, userId);

        return ResponseEntity
                .ok(
                        shorteningService.getUrl(
                                id,
                                userId
                        )
                );

    }

    @Operation(
            summary = "Get slug",
            description = "Allows users to fetch a slug"
    )
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ShortenResponse> getUrlBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal UUID userId
    ) {

        log.info("Get URL slug: {} for userId: {}", slug, userId);

        return ResponseEntity
                .ok(
                        shorteningService.getUrlBySlug(
                                slug,
                                userId
                        )
                );
    }

    @Operation(
            summary = "Delete an URL by its ID",
            description = "Allows users to delete their shortened URL"
    )
    @DeleteMapping("/id/{id}")
    public ResponseEntity<Void> deleteUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal UUID userId
    ) {

        log.info("Delete URL id: {} for userId: {}", id, userId);

        shorteningService.delete(id, userId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @Operation(
            summary = "Delete an URL by its slug",
            description = "Allows users to delete their slug of the shortened URL"
    )
    @DeleteMapping("/slug/{slug}")
    public ResponseEntity<Void> deleteUrl(
            @PathVariable String slug,
            @AuthenticationPrincipal UUID userId
    ) {

        log.debug("Deleting slug '{}'", slug);

        shorteningService.deleteUrl(slug, userId);

        return ResponseEntity
                .noContent()
                .build();
    }
}

