package com.minima-AI.analytics.clicks;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "click_hourly")
@Getter
@Setter
public class ClickHourly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_id", nullable = false)
    private Long urlId;

    @Column(nullable = false)
    private Instant hour;         // truncated to hour e.g. 2025-01-01T14:00:00Z

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    protected ClickHourly() {} // JPA requires no-arg constructor

    public ClickHourly(Long urlId, Instant hour, long clickCount) {
        this.urlId      = urlId;
        this.hour       = hour;
        this.clickCount = clickCount;
    }
}

