package com.minima-AI.analytics.clicks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.util.UUID;

public interface UrlOwnerRepository extends JpaRepository<UrlOwner, Long> {

    boolean existsByUrlIdAndUserId(Long urlId, UUID userId);

    @Modifying
    void deleteByUrlId(Long urlId);
}

