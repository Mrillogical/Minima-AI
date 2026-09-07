package com.minima-AI.auth.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// @Entity â€” tells JPA this class maps to a DB table
// @Table â€” explicitly names the table (never rely on JPA default naming)
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    // UUID primary key â€” safer than auto-increment (no enumeration attacks)
    // gen_random_uuid() runs in DB â€” matches our Liquibase migration
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // nullable = false enforces NOT NULL at JPA layer (Liquibase already does it at DB layer)
    // unique = true enforces UNIQUE at JPA layer â€” double protection
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // nullable â€” OAuth2 Google users have no password
    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 255)
    private String name;

    // @Enumerated(STRING) â€” stores "ROLE_FREE" in DB, not "0"
    // Never use ORDINAL â€” adding enum values breaks existing data
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Provider provider;

    @Column(nullable = false)
    private boolean verified;

    // Instant = timezone-safe â€” always UTC in DB, maps to TIMESTAMPTZ
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // updatable = false on createdAt â€” JPA never changes it after insert
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;  // JPA manages this â€” never set it manually

    // @PrePersist â€” runs automatically before INSERT
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();  // set on first save
        updatedAt = Instant.now();
    }

    // @PreUpdate â€” runs automatically before UPDATE
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();  // refresh on every update
    }
}

