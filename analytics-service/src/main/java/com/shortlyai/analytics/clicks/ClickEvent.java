package com.minima-AI.analytics.clicks;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "click_events")
@Getter  // Getters only â€” entity is effectively immutable after creation
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // uses BIGSERIAL from migration
    private Long id;

    @Column(name = "url_id", nullable = false)
    private Long urlId;

    @Column(nullable = false, length = 20)
    private String slug;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;    // hashed â€” never store raw IPs

    @Column(columnDefinition = "TEXT")
    private String referer;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "user_id")
    private java.util.UUID userId;

    // JPA requires a no-arg constructor - keep it protected so nothing else uses it
    protected ClickEvent() {}

    // Factory method - creates entity from Kafka event
    public static ClickEvent from(
            com.minima-AI.analytics.events.UrlClickedEvent event) {
        ClickEvent e = new ClickEvent();
        e.urlId     = event.urlId();
        e.slug      = event.slug();
        e.userAgent = event.userAgent();
        e.ipHash    = event.ipHash();
        e.referer   = event.referer();
        e.country   = event.country();
        e.city      = event.city();
        e.clickedAt = event.clickedAt() != null ? event.clickedAt() : Instant.now();
        e.userId = event.userId();
        return e;
    }
}

