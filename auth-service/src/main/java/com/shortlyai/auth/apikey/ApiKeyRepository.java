package com.minima-AI.auth.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    // List all keys for a user
    List<ApiKey> findAllByUserId(UUID userId);

    // Ownership check before delete - prevents user A deleting user B's key
    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);
}

