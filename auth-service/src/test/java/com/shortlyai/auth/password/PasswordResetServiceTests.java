package com.minima-AI.auth.password;

import com.minima-AI.auth.audit.AuditEventType;
import com.minima-AI.auth.audit.AuditLogService;
import com.minima-AI.auth.common.exception.InvalidTokenException;
import com.minima-AI.auth.common.exception.UserNotFoundException;
import com.minima-AI.auth.email.EmailService;
import com.minima-AI.auth.token.RefreshTokenService;
import com.minima-AI.auth.user.Provider;
import com.minima-AI.auth.user.Role;
import com.minima-AI.auth.user.User;
import com.minima-AI.auth.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTests {

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    UserRepository userRepository;

    @Mock
    EmailService emailService;

    @Mock
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    RefreshTokenService refreshTokenService;

    @Mock
    AuditLogService auditLogService;

    @Mock
    HttpServletRequest httpServletRequest;

    @InjectMocks
    PasswordResetService passwordResetService;

    private User localUser;

    private static final String REDIS_PREFIX = "reset:";

    @BeforeEach
    void setUp() {

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // lenient - not every test hits these headers, MockitoSettings.LENIENT
        // already covers it but explicit here for clarity
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("junit-test-agent");

        localUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("Test User")
                .role(Role.ROLE_FREE)
                .provider(Provider.LOCAL)
                .password("old-encoded-hash")
                .verified(true)
                .createdAt(Instant.now())
                .build();
    }

    // Helper - same convention as ApiKeyServiceTest: mock
    // TransactionSynchronizationManager as a static, capture the registered
    // synchronization, then manually fire afterCommit() to simulate the
    // transaction actually committing.
    private void resetPasswordAndCommit(String token, String newPassword) {

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            passwordResetService.resetPassword(token, newPassword, httpServletRequest);

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        syncCaptor.getValue().afterCommit();
    }

    // ---------- sendResetLink ----------

    @Test
    void sendResetLink_userNotFound_doesNothing() {

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        passwordResetService.sendResetLink("ghost@example.com", httpServletRequest);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));

        verifyNoInteractions(emailService);

        verifyNoInteractions(auditLogService);
    }

    @Test
    void sendResetLink_oauthUser_doesNotSendEmail() {

        User oauthUser = User.builder()
                .id(UUID.randomUUID())
                .email("oauth@example.com")
                .name("OAuth User")
                .role(Role.ROLE_FREE)
                .provider(Provider.GOOGLE)
                .password(null)
                .verified(true)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByEmail(oauthUser.getEmail())).thenReturn(Optional.of(oauthUser));

        passwordResetService.sendResetLink(oauthUser.getEmail(), httpServletRequest);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));

        verifyNoInteractions(emailService);

        verifyNoInteractions(auditLogService);
    }

    @Test
    void sendResetLink_localUser_storesTokenAsKey_userIdAsValue_with30MinTtl() {

        when(userRepository.findByEmail(localUser.getEmail())).thenReturn(Optional.of(localUser));

        passwordResetService.sendResetLink(localUser.getEmail(), httpServletRequest);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        // key must be reset:{token} - NOT reset:{userId}. This is the exact
        // inversion bug caught in review before this went in; asserting it
        // explicitly so it can never silently regress.
        assertThat(keyCaptor.getValue()).startsWith(REDIS_PREFIX);
        assertThat(keyCaptor.getValue()).doesNotContain(localUser.getId().toString());

        assertThat(valueCaptor.getValue()).isEqualTo(localUser.getId().toString());

        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void sendResetLink_localUser_delegatesToEmailServiceWithGeneratedToken() {

        when(userRepository.findByEmail(localUser.getEmail())).thenReturn(Optional.of(localUser));

        passwordResetService.sendResetLink(localUser.getEmail(), httpServletRequest);

        verify(emailService).sendResetPasswordEmail(
                eq(localUser.getEmail()),
                anyString(),           // generated UUID token
                eq(localUser.getName())
        );
    }

    @Test
    void sendResetLink_localUser_logsAuditEventWithIpAndUserAgent() {

        when(userRepository.findByEmail(localUser.getEmail())).thenReturn(Optional.of(localUser));

        passwordResetService.sendResetLink(localUser.getEmail(), httpServletRequest);

        verify(auditLogService).log(
                AuditEventType.PASSWORD_RESET_REQUESTED,
                localUser.getId(),
                "127.0.0.1",
                "junit-test-agent"
        );
    }

    // ---------- resetPassword ----------

    @Test
    void resetPassword_missingToken_throwsInvalidToken_touchesNothingElse() {

        when(valueOps.get(REDIS_PREFIX + "bad-token")).thenReturn(null);

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                "bad-token", "NewPassword123", httpServletRequest))
                .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).findById(any());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void resetPassword_tokenValidButUserDeleted_throwsUserNotFound() {

        UUID userId = UUID.randomUUID();

        String token = "valid-token";

        when(valueOps.get(REDIS_PREFIX + token)).thenReturn(userId.toString());

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                token, "NewPassword123", httpServletRequest))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void resetPassword_validToken_encodesAndSavesNewPassword() {

        String token = "valid-token";

        when(valueOps.get(REDIS_PREFIX + token)).thenReturn(localUser.getId().toString());

        when(userRepository.findById(localUser.getId())).thenReturn(Optional.of(localUser));

        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-encoded-hash");

        resetPasswordAndCommit(token, "NewPassword123");

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(savedCaptor.capture());

        assertThat(savedCaptor.getValue().getPassword()).isEqualTo("new-encoded-hash");
    }

    @Test
    void resetPassword_validToken_logsAuditEvent() {

        String token = "valid-token";

        when(valueOps.get(REDIS_PREFIX + token)).thenReturn(localUser.getId().toString());

        when(userRepository.findById(localUser.getId())).thenReturn(Optional.of(localUser));

        when(passwordEncoder.encode(anyString())).thenReturn("new-encoded-hash");

        resetPasswordAndCommit(token, "NewPassword123");

        verify(auditLogService).log(
                AuditEventType.PASSWORD_RESET_COMPLETED,
                localUser.getId(),
                "127.0.0.1",
                "junit-test-agent"
        );
    }

    @Test
    void resetPassword_beforeCommit_doesNotDeleteTokenOrRevokeSessions() {

        // Mirrors ApiKeyServiceTest's "fails closed" test: if the process
        // dies between the DB commit and the afterCommit() callback firing,
        // the reset token must still be sitting in Redis (so nothing is
        // silently lost) and no sessions must have been revoked yet.
        String token = "valid-token";

        when(valueOps.get(REDIS_PREFIX + token)).thenReturn(localUser.getId().toString());

        when(userRepository.findById(localUser.getId())).thenReturn(Optional.of(localUser));

        when(passwordEncoder.encode(anyString())).thenReturn("new-encoded-hash");

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            passwordResetService.resetPassword(token, "NewPassword123", httpServletRequest);

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()));
        }

        // afterCommit() deliberately never invoked here
        verify(userRepository).save(any(User.class));
        verify(stringRedisTemplate, never()).delete(anyString());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void resetPassword_afterCommit_deletesTokenAndRevokesAllSessions() {

        String token = "valid-token";

        when(valueOps.get(REDIS_PREFIX + token)).thenReturn(localUser.getId().toString());

        when(userRepository.findById(localUser.getId())).thenReturn(Optional.of(localUser));

        when(passwordEncoder.encode(anyString())).thenReturn("new-encoded-hash");

        resetPasswordAndCommit(token, "NewPassword123");

        verify(stringRedisTemplate).delete(REDIS_PREFIX + token);

        verify(refreshTokenService).revokeAllForUser(localUser.getId());
    }
}

