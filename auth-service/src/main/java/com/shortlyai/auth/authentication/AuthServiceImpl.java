package com.minima-AI.auth.authentication;

import com.minima-AI.auth.audit.AuditEventType;
import com.minima-AI.auth.audit.AuditLogService;
import com.minima-AI.auth.audit.RequestMetadataExtractor;
import com.minima-AI.auth.common.exception.*;
import com.minima-AI.auth.dto.*;
import com.minima-AI.auth.email.VerificationService;
import com.minima-AI.auth.security.JwtUtil;
import com.minima-AI.auth.token.RefreshTokenService;
import com.minima-AI.auth.user.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    private final UserMapper userMapper;

    private final RefreshTokenService refreshTokenService;

    private final AuditLogService auditLogService;

    private final VerificationService verificationService;

    @Override
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        log.info("Login attempt for email: {}", request.email());

        // Extract synchronously, on this thread, BEFORE any @Async audit call
        String ip = RequestMetadataExtractor.extractIp(httpRequest);
        String userAgent = RequestMetadataExtractor.extractUserAgent(httpRequest);

        // find user, audit failure if not found
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {

                    log.warn("Login failed, email not found: {}", request.email());

                    auditLogService.log(AuditEventType.LOGIN_FAILED, null, ip, userAgent);

                    return new InvalidCredentialsException("Invalid email or password");
                });

        // verify password, audit failure if wrong
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {

            log.warn("Login failed, wrong password for email: {}", request.email());

            auditLogService.log(AuditEventType.LOGIN_FAILED, user.getId(), ip, userAgent);

            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Account verification check
        if (!user.isVerified()) {
            throw new AccountNotVerifiedException("Please verify your email before logging in");
        }

        // generate tokens
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        // Store refresh token
        refreshTokenService.store(refreshToken, user.getId().toString());

        // Audit success - async execution
        auditLogService.log(AuditEventType.LOGIN_SUCCESS, user.getId(), ip, userAgent);

        log.info("Login successful for userId: {}", user.getId());

        return new AuthResponse(accessToken, refreshToken, userMapper.toResponse(user));
    }

    @Override
    public AuthResponse register(RegisterRequest registerRequest, HttpServletRequest httpRequest) {

        log.info("Register attempt for email: {}", registerRequest.email());

        String ip = RequestMetadataExtractor.extractIp(httpRequest);
        String userAgent = RequestMetadataExtractor.extractUserAgent(httpRequest);

        if (userRepository.existsByEmail(registerRequest.email())) {

            auditLogService.log(AuditEventType.REGISTER_FAILED, null, ip, userAgent);

            throw new EmailAlreadyExistsException("An account exists already with this email");
        }

        User user = User.builder()
                .name(registerRequest.name())
                .email(registerRequest.email())
                .password(passwordEncoder.encode(registerRequest.password()))
                .role(Role.ROLE_FREE)
                .provider(Provider.LOCAL)
                .verified(false)
                .build();

        User savedUser = userRepository.save(user);

        auditLogService.log(AuditEventType.REGISTER, savedUser.getId(), ip, userAgent);

        log.info("Registration successful for userId: {}", savedUser.getId());

        verificationService.sendVerificationEmail(savedUser);

        log.info("Verification mail sent for userId: {}", savedUser.getId());

        // No tokens issued here. login() blocks unverified accounts via isVerified()
        return new AuthResponse(null, null, userMapper.toResponse(savedUser));
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {

        if (!jwtUtil.isTokenValid(request.refreshToken()) || jwtUtil.isAccessToken(request.refreshToken())) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        if (!refreshTokenService.exists(request.refreshToken())) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        String id = jwtUtil.extractUserId(request.refreshToken());

        User user = userRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String accessToken = jwtUtil
                .generateAccessToken(
                        user.getId(),
                        user.getEmail(),
                        user.getRole().name()
                );

        String refreshToken = jwtUtil
                .generateRefreshToken(
                        user.getId(),
                        user.getEmail(),
                        user.getRole().name()
                );

        refreshTokenService.delete(request.refreshToken());
        refreshTokenService.store(refreshToken, id);

        auditLogService.log(
                AuditEventType.TOKEN_REFRESH,
                user.getId(),
                RequestMetadataExtractor.extractIp(httpRequest),
                RequestMetadataExtractor.extractUserAgent(httpRequest)
        );

        log.info("New refresh token generated for userId: {}", id);

        return new AuthResponse(accessToken, refreshToken, userMapper.toResponse(user));
    }

    @Override
    public void logout(RefreshTokenRequest request, HttpServletRequest httpRequest) {

        if (!refreshTokenService.exists(request.refreshToken())) {
            throw new InvalidTokenException("Invalid or already expired refresh token");
        }

        String userId = jwtUtil.extractUserId(request.refreshToken());

        refreshTokenService.delete(request.refreshToken());

        auditLogService.log(
                AuditEventType.LOGOUT,
                UUID.fromString(userId),
                RequestMetadataExtractor.extractIp(httpRequest),
                RequestMetadataExtractor.extractUserAgent(httpRequest)
        );

        log.info("Logout successful for userId: {}", userId);
    }

    @Override
    public UserResponse getMe(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("This user does not exist"));

        return userMapper.toResponse(user);
    }
}

