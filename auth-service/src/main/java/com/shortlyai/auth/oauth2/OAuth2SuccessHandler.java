package com.minima-AI.auth.oauth2;

import com.minima-AI.auth.audit.AuditEventType;
import com.minima-AI.auth.audit.AuditLogService;
import com.minima-AI.auth.audit.RequestMetadataExtractor;
import com.minima-AI.auth.security.JwtUtil;
import com.minima-AI.auth.token.RefreshTokenService;
import com.minima-AI.auth.user.Provider;
import com.minima-AI.auth.user.Role;
import com.minima-AI.auth.user.User;
import com.minima-AI.auth.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    private final JwtUtil jwtUtil;

    private final RefreshTokenService refreshTokenService;

    private final AuditLogService auditLogService;

    private final String redirectUri;

    public OAuth2SuccessHandler(
            UserRepository userRepository,
            JwtUtil jwtUtil,
            RefreshTokenService refreshTokenService,
            AuditLogService auditLogService,
            @Value("${oauth2.redirect-uri}") String redirectUri
    ) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.redirectUri = redirectUri;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        // Step 1
        // Authentication object contains Google's profile
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        // Step 2
        // If user exists -> return them
        // If not -> create new User with Provider.GOOGLE, Role.ROLE_FREE, verified=true
        // Google already verified the email - no verification step needed
        User user = userRepository.findByEmail(email)
                .map(existing -> { // If user already logged in and tries to log in from Google
                            if (!existing.isVerified()) {

                                existing.setVerified(true);

                                userRepository.save(existing);
                            }
                            return existing;
                        }
                ).orElseGet(() -> { // new user

                    User newUser = User.builder()
                            .name(name)
                            .email(email)
                            .password(null)
                            .role(Role.ROLE_FREE)
                            .provider(Provider.GOOGLE)
                            .verified(true)
                            .build();

                    return userRepository.save(newUser);
                });

        // Step 3
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

        // Step 4
        refreshTokenService.store(refreshToken, user.getId().toString());

        // Audit
        auditLogService.log(
                AuditEventType.OAUTH2_LOGIN,
                user.getId(),
                RequestMetadataExtractor.extractIp(request),
                RequestMetadataExtractor.extractUserAgent(request)
        );

        // Step 5
        // Frontend reads tokens from the URL fragment and stores them.
        // Fragments are browser-only, never sent in the request, never in Referer header
        String redirectUrl = redirectUri
                + "#accessToken=" + accessToken
                + "&refreshToken=" + refreshToken;

        log.info("OAuth2 login successful for userId: {}", user.getId());

        response.sendRedirect(redirectUrl);
    }
}

