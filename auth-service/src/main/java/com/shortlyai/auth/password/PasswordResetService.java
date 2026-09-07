package com.minima-AI.auth.password;

import com.minima-AI.auth.audit.AuditEventType;
import com.minima-AI.auth.audit.AuditLogService;
import com.minima-AI.auth.audit.RequestMetadataExtractor;
import com.minima-AI.auth.common.exception.InvalidTokenException;
import com.minima-AI.auth.common.exception.UserNotFoundException;
import com.minima-AI.auth.email.EmailService;
import com.minima-AI.auth.token.RefreshTokenService;
import com.minima-AI.auth.user.Provider;
import com.minima-AI.auth.user.User;
import com.minima-AI.auth.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final StringRedisTemplate stringRedisTemplate;

    private final UserRepository userRepository;

    private final EmailService emailService;

    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenService refreshTokenService;

    private final AuditLogService auditLogService;

    private static final String REDIS_KEY_PREFIX = "reset:";

    public void sendResetLink(String email, HttpServletRequest request) {

        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {

            log.debug("User with email '{}' not found, reset password email not sent", email);

            return;
        }

        User user = optionalUser.get();

        if (user.getProvider() != Provider.LOCAL) {

            log.debug("User {} signed up via {}, reset password email not sent", user.getId(), user.getProvider());

            return;
        }

        String token = UUID.randomUUID().toString();

        // key = reset:{token} -> value = userId. Lookup at reset time only
        // has the token (from the email link), never the userId - so the
        // token has to be the key, not the value.
        stringRedisTemplate.opsForValue()
                .set(
                        REDIS_KEY_PREFIX + token,
                        user.getId().toString(),
                        Duration.ofMinutes(30)
                );

        emailService.sendResetPasswordEmail(
                user.getEmail(),
                token,
                user.getName()
        );

        auditLogService.log(
                AuditEventType.PASSWORD_RESET_REQUESTED,
                user.getId(),
                RequestMetadataExtractor.extractIp(request),
                RequestMetadataExtractor.extractUserAgent(request)
        );

        log.info("Reset password email sent to user: {}, email: {}", user.getId(), user.getEmail());
    }

    @Transactional
    public void resetPassword(String token, String newPassword, HttpServletRequest request) {

        String userId = stringRedisTemplate
                .opsForValue()
                .get(REDIS_KEY_PREFIX + token);

        if (userId == null || userId.isEmpty()) {

            throw new InvalidTokenException("Invalid or expired reset password token");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));

        userRepository.save(user);

        auditLogService.log(
                AuditEventType.PASSWORD_RESET_COMPLETED,
                user.getId(),
                RequestMetadataExtractor.extractIp(request),
                RequestMetadataExtractor.extractUserAgent(request)
        );

        log.info("Password changed for user: {}", user.getId());

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {

                                stringRedisTemplate.delete(REDIS_KEY_PREFIX + token);

                                // Password reset should log the user out
                                // everywhere - if the account was
                                // compromised, changing the password alone
                                // doesn't help if the attacker's session
                                // just keeps working.
                                refreshTokenService.revokeAllForUser(UUID.fromString(userId));
                            }
                        }
                );
    }
}

