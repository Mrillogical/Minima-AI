package com.minima-AI.auth.token;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // token's id

    @Column(name = "user_id", nullable = false)
    private UUID userId; // belongs to which user

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash; // stored token

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}


