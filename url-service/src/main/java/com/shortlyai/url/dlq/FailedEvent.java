package com.minima-AI.url.dlq;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "failed_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String topic;         // Kafka topic that failed

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;      // Kafka message key (slug)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;       // JSON-serialized event body

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;  // exception message from Kafka failure

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;   // incremented on each retry attempt

    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false; // true once successfully republished

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt; // null until first retry attempt

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

