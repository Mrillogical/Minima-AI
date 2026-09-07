package com.minima-AI.auth.apikey;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FK to users.id - set on creation, never changes
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // First 8 chars of random portion - shown in list view so user can identify keys
    // e.g. "ab12cd34" -> user sees "sk_ab12cd34..."
    @Column(name = "key_prefix", nullable = false, updatable = false, length = 8)
    private String keyPrefix;

    // SHA-256 hex of the full raw key - 64 chars
    // Only this is persisted, raw key is never stored
    @Column(name = "key_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String keyHash;

    // User-given label for the key - "Cursor plugin", "n8n workflow"
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

