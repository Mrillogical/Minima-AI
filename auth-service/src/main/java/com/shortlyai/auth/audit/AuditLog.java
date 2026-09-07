package com.minima-AI.auth.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// @Entity â€” maps to audit_log table
// Append-only â€” never update audit records, only insert
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    // BIGSERIAL in DB â€” use Long here, not UUID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable â€” login failures have no authenticated userId yet
    // ON DELETE SET NULL in migration â€” user deleted, log stays
    @Column(name = "user_id")
    private UUID userId;

    // LOGIN_SUCCESS, LOGIN_FAILED, REGISTER, LOGOUT, TOKEN_REFRESH
    // Enum as String â€” readable in DB, safe to add new values later
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private AuditEventType eventType;

    // IPv4 = 15 chars, IPv6 = 45 chars â€” column sized for IPv6
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // User-Agent header â€” browser/client fingerprint, TEXT = unlimited length
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    // updatable = false â€” DB sets this on insert, JPA never changes it
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // @PrePersist â€” auto-set timestamp before every INSERT
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

