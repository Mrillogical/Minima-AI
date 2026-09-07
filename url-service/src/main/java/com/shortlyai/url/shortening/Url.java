package com.minima-AI.url.shortening;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// @Entity â€” JPA maps this class to the "urls" table in Postgres
// @Table â€” explicitly names the table, never rely on JPA's default naming
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "urls")
public class Url {

    // BIGSERIAL in Postgres â€” auto-incremented Long in Java
    // IDENTITY strategy tells JPA to let Postgres generate the value
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The Base62-encoded short code â€” indexed for fast redirect lookups
    // unique = true mirrors the UNIQUE constraint in our migration
    @Column(nullable = false, unique = true, length = 20)
    @Size(max = 20, message = "Custom slug cannot exceed 20 characters")
    @Pattern(
            regexp = "^[a-zA-Z0-9_-]+$",
            message = "Custom slug can only contain letters, numbers, hyphens, and underscores"
    )
    private String slug;

    // Original long URL â€” TEXT in Postgres, no length limit
    // columnDefinition overrides JPA's default VARCHAR
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    // Owner's user ID
    // auth-service owns the users table â€” cross-service FK = anti-pattern
    @Column(nullable = false)
    private UUID userId;

    // Page title â€” null until ai-service processes the URL
    @Column(length = 500)
    private String title;

    // AI classification result â€” null until ai-service responds
    @Column(length = 100)
    private String category;

    // AI safety check â€” defaults true, updated when ai-service responds
    @Column(nullable = false)
    @Builder.Default
    private boolean isSafe = true;

    // Was this slug user-provided or system-generated via Base62?
    @Column(nullable = false)
    @Builder.Default
    private boolean isCustom = false;

    // Soft delete â€” never hard-delete URLs, analytics still references them
    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    // Denormalized click counter â€” incremented on every redirect
    // analytics-service owns detailed click data, this is for fast reads
    @Column(nullable = false)
    @Builder.Default
    private long clickCount = 0;

    // Null = never expires â€” set to NOW() + 30 days by default in service layer
    @Column
    private Instant expiresAt;

    // Always UTC â€” TIMESTAMPTZ in Postgres
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // @PrePersist â€” JPA calls this automatically before every INSERT
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    // @PreUpdate â€” JPA calls this automatically before every UPDATE
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

