package com.minima-AI.auth.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // Takes plain Strings, not HttpServletRequest
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEventType eventType, UUID userId, String ipAddress, String userAgent) {

        AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(entry);

        log.debug("Audit logged: event= {}, userId= {}, ip= {}", eventType, userId, ipAddress);
    }
}

