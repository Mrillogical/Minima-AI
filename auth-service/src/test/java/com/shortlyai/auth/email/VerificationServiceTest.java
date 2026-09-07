package com.minima-AI.auth.email;

import com.minima-AI.auth.common.exception.InvalidTokenException;
import com.minima-AI.auth.common.exception.UserNotFoundException;
import com.minima-AI.auth.user.Provider;
import com.minima-AI.auth.user.Role;
import com.minima-AI.auth.user.User;
import com.minima-AI.auth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
class VerificationServiceTest {

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    UserRepository userRepository;

    @Mock
    EmailService emailService;

    @InjectMocks
    VerificationService verificationService;

    private User unverifiedUser;

    private static final String REDIS_PREFIX = "verify:";

    @BeforeEach
    void setUp() {

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        unverifiedUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("Test User")
                .role(Role.ROLE_FREE)
                .provider(Provider.LOCAL)
                .verified(false)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void sendVerificationEmail_storesTokenInRedisWithTtl() {

        verificationService.sendVerificationEmail(unverifiedUser);

        // token stored with "verify:" prefix and 2-hour TTL
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).startsWith(REDIS_PREFIX);

        assertThat(valueCaptor.getValue()).isEqualTo(unverifiedUser.getId().toString());

        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void sendVerificationEmail_delegatesToEmailService() {

        verificationService.sendVerificationEmail(unverifiedUser);

        verify(emailService).sendVerificationEmail(
                eq(unverifiedUser.getEmail()),
                anyString(),            // generated token UUID
                eq(unverifiedUser.getName())
        );
    }

    @Test
    void verifyAccount_expiredOrInvalidToken_throwsInvalidToken() {

        String token = "expired-token";

        when(valueOps.get(REDIS_PREFIX + token)).thenReturn(null);

        assertThatThrownBy(() -> verificationService.verifyAccount(token))
                .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).findById(any());

        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void verifyAccount_tokenValidButUserDeleted_throwsUserNotFound() {

        String token = UUID.randomUUID().toString();

        UUID userId = UUID.randomUUID();

        when(valueOps.get(REDIS_PREFIX + token)).thenReturn(userId.toString());

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.verifyAccount(token))
                .isInstanceOf(UserNotFoundException.class);

        verify(stringRedisTemplate, never()).delete(anyString());
    }
}

