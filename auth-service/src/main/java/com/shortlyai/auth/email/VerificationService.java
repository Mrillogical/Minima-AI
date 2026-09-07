package com.minima-AI.auth.email;

import com.minima-AI.auth.common.exception.InvalidTokenException;
import com.minima-AI.auth.common.exception.UserNotFoundException;
import com.minima-AI.auth.user.User;
import com.minima-AI.auth.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationService {

    private final StringRedisTemplate stringRedisTemplate;

    private final UserRepository userRepository;

    private final EmailService emailService;

    private static final String REDIS_KEY_PREFIX = "verify:";

    public void sendVerificationEmail(User user) {

        // Generate verification token
        String token = UUID.randomUUID().toString();

        // Store it in Redis
        stringRedisTemplate.opsForValue()
                .set(REDIS_KEY_PREFIX + token,
                        user.getId().toString(),
                        Duration.of(2, ChronoUnit.HOURS)
                );

        // send email - async task
        emailService.sendVerificationEmail(user.getEmail(), token, user.getName());

        log.info("Account verification mail sent to user: {}", user.getName());

    }

    @Transactional
    public void verifyAccount(String token) {

        // get userId from Redis
        String userId = stringRedisTemplate.opsForValue()
                .get(REDIS_KEY_PREFIX + token);

        if (userId == null) {
            throw new InvalidTokenException("Invalid or expired verification token");
        }

        // find user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // account verified
        user.setVerified(true);

        userRepository.save(user);

        TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {

                            @Override
                            public void afterCommit() {

                                stringRedisTemplate.delete(REDIS_KEY_PREFIX + token);
                            }
                        }
                );

        log.info("Account verified: {} and deleted verification token from Redis: {}", user.getName(), REDIS_KEY_PREFIX + token);
    }
}

