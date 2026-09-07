package com.minima-AI.auth.oauth2;

import com.minima-AI.auth.audit.AuditLogService;
import com.minima-AI.auth.security.JwtUtil;
import com.minima-AI.auth.token.RefreshTokenService;
import com.minima-AI.auth.user.Provider;
import com.minima-AI.auth.user.Role;
import com.minima-AI.auth.user.User;
import com.minima-AI.auth.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2SuccessHandlerTests {

    @Mock
    UserRepository userRepository;

    @Mock
    JwtUtil jwtUtil;

    @Mock
    RefreshTokenService refreshTokenService;

    @Mock
    AuditLogService auditLogService;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    Authentication authentication;

    @Mock
    OAuth2User oAuth2User;

    OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(
                userRepository, jwtUtil, refreshTokenService, auditLogService,
                "https://app.minima-AI.dev/oauth2/callback"
        );

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
    }

    @Test
    void newGoogleUser_createsAccountWithGoogleProviderAndVerifiedTrue() throws Exception {

        UUID newUserId = UUID.randomUUID();

        when(oAuth2User.getAttribute("email")).thenReturn("newperson@example.com");
        when(oAuth2User.getAttribute("name")).thenReturn("New Person");

        when(userRepository.findByEmail("newperson@example.com")).thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(newUserId);
            return u;
        });

        when(jwtUtil.generateAccessToken(eq(newUserId), any(), any())).thenReturn("access-tok");
        when(jwtUtil.generateRefreshToken(eq(newUserId), any(), any())).thenReturn("refresh-tok");

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(savedUser.getRole()).isEqualTo(Role.ROLE_FREE);
        assertThat(savedUser.isVerified()).isTrue();
        assertThat(savedUser.getPassword()).isNull();

        verify(refreshTokenService).store("refresh-tok", newUserId.toString());
    }

    @Test
    void existingUnverifiedLocalUser_googleLoginFlipsVerifiedToTrue() throws Exception {

        UUID existingId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(existingId)
                .email("person@example.com")
                .provider(Provider.LOCAL)
                .role(Role.ROLE_FREE)
                .verified(false) // signed up locally but never verified email
                .build();

        when(oAuth2User.getAttribute("email")).thenReturn("person@example.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Person");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(existingUser));

        when(jwtUtil.generateAccessToken(eq(existingId), any(), any())).thenReturn("access-tok");
        when(jwtUtil.generateRefreshToken(eq(existingId), any(), any())).thenReturn("refresh-tok");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(existingUser.isVerified()).isTrue();
        verify(userRepository).save(existingUser);

        assertThat(existingUser.getProvider()).isEqualTo(Provider.LOCAL);
    }

    @Test
    void existingVerifiedUser_doesNotRewriteAlreadyVerifiedFlag() throws Exception {

        UUID existingId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(existingId).email("verified@example.com")
                .provider(Provider.GOOGLE).role(Role.ROLE_FREE).verified(true)
                .build();

        when(oAuth2User.getAttribute("email")).thenReturn("verified@example.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Verified");
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(existingUser));
        when(jwtUtil.generateAccessToken(eq(existingId), any(), any())).thenReturn("a");
        when(jwtUtil.generateRefreshToken(eq(existingId), any(), any())).thenReturn("r");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userRepository, never()).save(any());
    }

    @Test
    void onSuccess_redirectsWithTokensAsQueryParams() throws Exception {

        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("x@example.com")
                .provider(Provider.GOOGLE).role(Role.ROLE_FREE).verified(true).build();

        when(oAuth2User.getAttribute("email")).thenReturn("x@example.com");
        when(oAuth2User.getAttribute("name")).thenReturn("X");
        when(userRepository.findByEmail("x@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(eq(userId), any(), any())).thenReturn("ACCESS123");
        when(jwtUtil.generateRefreshToken(eq(userId), any(), any())).thenReturn("REFRESH456");

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(urlCaptor.capture());

        assertThat(urlCaptor.getValue())
                .startsWith("https://app.minima-AI.dev/oauth2/callback#")
                .contains("accessToken=ACCESS123")
                .contains("refreshToken=REFRESH456");
    }
}

