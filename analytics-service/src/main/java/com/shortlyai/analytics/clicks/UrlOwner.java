package com.minima-AI.analytics.clicks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "url_owners")
@Getter
public class UrlOwner {

    @Id
    @Column(name = "url_id")
    private Long urlId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected UrlOwner() {}

    public UrlOwner(Long urlId, UUID userId) {
        this.urlId = urlId;
        this.userId = userId;
    }
}

