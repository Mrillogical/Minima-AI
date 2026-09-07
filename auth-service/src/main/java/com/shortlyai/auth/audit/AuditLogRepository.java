package com.minima-AI.auth.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// JpaRepository gives save(), that's all audit needs
// Append-only: no update, no delete (audit records are permanent)
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}

