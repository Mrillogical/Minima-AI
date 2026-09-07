package com.minima-AI.auth.authentication;

import com.minima-AI.auth.audit.AuditEventType;
import com.minima-AI.auth.audit.AuditLogService;
import com.minima-AI.auth.common.exception.*;
import com.minima-AI.auth.dto.*;
import com.minima-AI.auth.email.VerificationService;
import com.minima-AI.auth.security.JwtUtil;
import com.minima-AI.auth.token.RefreshTokenService;
import com.minima-AI.auth.user.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private VerificationService verificationService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private UserResponse userResponse;
    private UUID userId;

    @BeforeEach
    void setUp() {

        userId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .name("Test User")
                .email("user@example.com")
                .password("$2a$12$encodedPassword")
                .role(Role.ROLE_FREE)
                .provider(Provider.LOCAL)
                .verified(true)
                .createdAt(Instant.now())
                .build();

        userResponse = new UserResponse(
                userId,
                "Test User",
                "user@example.com",
                Role.ROLE_FREE,
                Provider.LOCAL,
                true,
                Instant.now()
        );

        when(httpRequest.getHeader("X-Forwarded-For"))
                .thenReturn("192.168.1.10");

        when(httpRequest.getHeader("User-Agent"))
                .thenReturn("JUnit");

        when(httpRequest.getRemoteAddr())
                .thenReturn("127.0.0.1");
    }

    @Test
    void login_validCredentials_returnsTokens() {

        LoginRequest request =
                new LoginRequest("user@example.com", "password123");

        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(testUser));

        when(passwordEncoder.matches("password123", testUser.getPassword()))
                .thenReturn(true);

        when(jwtUtil.generateAccessToken(
                userId,
                testUser.getEmail(),
                "ROLE_FREE"))
                .thenReturn("access-token");

        when(jwtUtil.generateRefreshToken(
                userId,
                testUser.getEmail(),
                "ROLE_FREE"))
                .thenReturn("refresh-token");

        when(userMapper.toResponse(testUser))
                .thenReturn(userResponse);

        AuthResponse response =
                authService.login(request, httpRequest);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user()).isEqualTo(userResponse);

        verify(refreshTokenService)
                .store("refresh-token", userId.toString());

        verify(auditLogService)
                .log(
                        AuditEventType.LOGIN_SUCCESS,
                        userId,
                        "192.168.1.10",
                        "JUnit"
                );
    }

    @Test
    void login_emailNotFound_throwsInvalidCredentials() {

        LoginRequest request =
                new LoginRequest("unknown@example.com", "password123");

        when(userRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(request, httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditLogService)
                .log(
                        eq(AuditEventType.LOGIN_FAILED),
                        isNull(),
                        eq("192.168.1.10"),
                        eq("JUnit")
                );

        verify(refreshTokenService, never())
                .store(any(), any());
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {

        LoginRequest request =
                new LoginRequest("user@example.com", "wrong-password");

        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(testUser));

        when(passwordEncoder.matches(
                "wrong-password",
                testUser.getPassword()))
                .thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(request, httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditLogService)
                .log(
                        AuditEventType.LOGIN_FAILED,
                        userId,
                        "192.168.1.10",
                        "JUnit"
                );

        verify(refreshTokenService, never())
                .store(any(), any());
    }

    @Test
    void login_unverifiedAccount_throwsException() {

        testUser.setVerified(false);

        LoginRequest request =
                new LoginRequest("user@example.com", "password123");

        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(testUser));

        when(passwordEncoder.matches(
                "password123",
                testUser.getPassword()))
                .thenReturn(true);

        assertThatThrownBy(() ->
                authService.login(request, httpRequest))
                .isInstanceOf(AccountNotVerifiedException.class);

        verify(refreshTokenService, never()).store(any(), any());
    }

    @Test
    void register_newEmail_savesUserButIssuesNoTokens() {

        RegisterRequest request =
                new RegisterRequest(
                        "New User",
                        "new@example.com",
                        "password123"
                );

        when(userRepository.existsByEmail("new@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("password123"))
                .thenReturn("$2a$12$encoded");

        when(userRepository.save(any(User.class)))
                .thenReturn(testUser);

        when(userMapper.toResponse(testUser))
                .thenReturn(userResponse);

        AuthResponse response =
                authService.register(request, httpRequest);

        // register() no longer issues tokens - login() gates on isVerified(),
        // and a token with no "verified" claim would bypass that check entirely.
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(response.user()).isEqualTo(userResponse);

        verify(userRepository).save(any(User.class));

        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
        verify(jwtUtil, never()).generateRefreshToken(any(), any(), any());
        verify(refreshTokenService, never()).store(any(), any());

        verify(auditLogService)
                .log(
                        AuditEventType.REGISTER,
                        userId,
                        "192.168.1.10",
                        "JUnit"
                );

        verify(verificationService)
                .sendVerificationEmail(testUser);
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExists() {

        RegisterRequest request =
                new RegisterRequest(
                        "Test",
                        "user@example.com",
                        "password123"
                );

        when(userRepository.existsByEmail("user@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() ->
                authService.register(request, httpRequest))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never())
                .save(any());

        verify(auditLogService)
                .log(
                        eq(AuditEventType.REGISTER_FAILED),
                        isNull(),
                        eq("192.168.1.10"),
                        eq("JUnit")
                );
    }

    @Test
    void refresh_validToken_rotatesTokens() {

        String oldToken = "old-refresh-token";

        RefreshTokenRequest request =
                new RefreshTokenRequest(oldToken);

        when(jwtUtil.isTokenValid(oldToken))
                .thenReturn(true);

        when(jwtUtil.isAccessToken(oldToken))
                .thenReturn(false);

        when(refreshTokenService.exists(oldToken))
                .thenReturn(true);

        when(jwtUtil.extractUserId(oldToken))
                .thenReturn(userId.toString());

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(testUser));

        when(jwtUtil.generateAccessToken(
                userId,
                testUser.getEmail(),
                "ROLE_FREE"))
                .thenReturn("new-access");

        when(jwtUtil.generateRefreshToken(
                userId,
                testUser.getEmail(),
                "ROLE_FREE"))
                .thenReturn("new-refresh");

        when(userMapper.toResponse(testUser))
                .thenReturn(userResponse);

        AuthResponse response =
                authService.refresh(request, httpRequest);

        assertThat(response.accessToken())
                .isEqualTo("new-access");

        assertThat(response.refreshToken())
                .isEqualTo("new-refresh");

        assertThat(response.user())
                .isEqualTo(userResponse);

        verify(refreshTokenService)
                .delete(oldToken);

        verify(refreshTokenService)
                .store("new-refresh", userId.toString());

        verify(auditLogService)
                .log(
                        AuditEventType.TOKEN_REFRESH,
                        userId,
                        "192.168.1.10",
                        "JUnit"
                );
    }

    @Test
    void refresh_invalidJwt_throwsInvalidToken() {

        RefreshTokenRequest request =
                new RefreshTokenRequest("invalid-token");

        when(jwtUtil.isTokenValid("invalid-token"))
                .thenReturn(false);

        assertThatThrownBy(() ->
                authService.refresh(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenService, never())
                .exists(any());

        verify(refreshTokenService, never())
                .store(any(), any());

        verify(refreshTokenService, never())
                .delete(any());
    }

    @Test
    void refresh_accessTokenRejected() {

        String token = "access-token";

        RefreshTokenRequest request =
                new RefreshTokenRequest(token);

        when(jwtUtil.isTokenValid(token))
                .thenReturn(true);

        when(jwtUtil.isAccessToken(token))
                .thenReturn(true);

        assertThatThrownBy(() ->
                authService.refresh(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenService, never())
                .exists(any());

        verify(refreshTokenService, never())
                .store(any(), any());

        verify(refreshTokenService, never())
                .delete(any());
    }

    @Test
    void refresh_tokenNotInRedis_throwsInvalidToken() {

        String token = "valid-but-revoked";

        RefreshTokenRequest request =
                new RefreshTokenRequest(token);

        when(jwtUtil.isTokenValid(token))
                .thenReturn(true);

        when(jwtUtil.isAccessToken(token))
                .thenReturn(false);

        when(refreshTokenService.exists(token))
                .thenReturn(false);

        assertThatThrownBy(() ->
                authService.refresh(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenService, never())
                .store(any(), any());

        verify(refreshTokenService, never())
                .delete(any());
    }

    @Test
    void refresh_userNotFound_throwsException() {

        String token = "valid-refresh";

        RefreshTokenRequest request =
                new RefreshTokenRequest(token);

        when(jwtUtil.isTokenValid(token))
                .thenReturn(true);

        when(jwtUtil.isAccessToken(token))
                .thenReturn(false);

        when(refreshTokenService.exists(token))
                .thenReturn(true);

        when(jwtUtil.extractUserId(token))
                .thenReturn(userId.toString());

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.refresh(request, httpRequest))
                .isInstanceOf(UserNotFoundException.class);

        verify(refreshTokenService, never())
                .store(any(), any());

        verify(refreshTokenService, never())
                .delete(any());
    }

    @Test
    void logout_validToken_deletesFromRedis() {

        String token = "valid-refresh";

        RefreshTokenRequest request =
                new RefreshTokenRequest(token);

        when(jwtUtil.isTokenValid(token))
                .thenReturn(true);

        when(jwtUtil.isAccessToken(token))
                .thenReturn(false);

        when(refreshTokenService.exists(token))
                .thenReturn(true);

        when(jwtUtil.extractUserId(token))
                .thenReturn(userId.toString());

        authService.logout(request, httpRequest);

        verify(refreshTokenService)
                .delete(token);

        verify(auditLogService)
                .log(
                        AuditEventType.LOGOUT,
                        userId,
                        "192.168.1.10",
                        "JUnit"
                );
    }

    @Test
    void logout_invalidJwt_throwsInvalidToken() {

        String token = "bad-token";

        RefreshTokenRequest request =
                new RefreshTokenRequest(token);

        when(jwtUtil.isTokenValid(token))
                .thenReturn(false);

        assertThatThrownBy(() ->
                authService.logout(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenService, never())
                .delete(any());

        verify(auditLogService, never())
                .log(any(), any(), any(), any());
    }

    @Test
    void logout_accessTokenRejected() {

        String token = "access-token";

        RefreshTokenRequest request =
                new RefreshTokenRequest(token);

        when(jwtUtil.isTokenValid(token))
                .thenReturn(true);

        when(jwtUtil.isAccessToken(token))
                .thenReturn(true);

        assertThatThrownBy(() ->
                authService.logout(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenService, never())
                .delete(any());

        verify(auditLogService, never())
                .log(any(), any(), any(), any());
    }

    @Test
    void logout_tokenNotInRedis_throwsInvalidToken() {

        String token = "stale-token";

        RefreshTokenRequest request =
                new RefreshTokenRequest(token);

        when(jwtUtil.isTokenValid(token))
                .thenReturn(true);

        when(jwtUtil.isAccessToken(token))
                .thenReturn(false);

        when(refreshTokenService.exists(token))
                .thenReturn(false);

        assertThatThrownBy(() ->
                authService.logout(request, httpRequest))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenService, never())
                .delete(any());

        verify(auditLogService, never())
                .log(any(), any(), any(), any());
    }

    @Test
    void getMe_existingUser_returnsUserResponse() {

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(testUser));

        when(userMapper.toResponse(testUser))
                .thenReturn(userResponse);

        UserResponse result = authService.getMe(userId);

        assertThat(result)
                .isEqualTo(userResponse);

        verify(userRepository)
                .findById(userId);

        verify(userMapper)
                .toResponse(testUser);
    }

    @Test
    void getMe_unknownUser_throwsUserNotFound() {

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.getMe(userId))
                .isInstanceOf(UserNotFoundException.class);

        verify(userMapper, never())
                .toResponse(any());
    }
}

