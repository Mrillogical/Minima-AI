package com.minima-AI.auth.authentication;

import com.minima-AI.auth.dto.*;
import com.minima-AI.auth.email.VerificationService;
import com.minima-AI.auth.password.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication Controller")
public class AuthController {

    private final AuthService authService;

    private final VerificationService verificationService;

    private final PasswordResetService passwordResetService;

    @Operation(
            summary = "Login",
            description = "Allows users to login"
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity.ok(authService.login(loginRequest, httpRequest));
    }

    @Operation(
            summary = "Register",
            description = "Allows users to register for minima-AI"
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest registerRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(registerRequest, httpRequest));
    }

    @Operation(
            summary = "Refresh tokens",
            description = "Generates a refresh JWT token"
    )
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(authService.refresh(refreshTokenRequest, httpRequest));
    }

    @Operation(
            summary = "Logout",
            description = "Allows users to logout"
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest httpRequest
    ) {

        authService.logout(refreshTokenRequest, httpRequest);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @Operation(
            summary = "Current user",
            description = "Extracts current user's details"
    )
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal String userId
    ) {

        return ResponseEntity.ok(authService.getMe(UUID.fromString(userId)));
    }

    @Operation(
            summary = "Account verification",
            description = "Allows users to verify their email"
    )
    @GetMapping("/verify")
    public ResponseEntity<Void> accountVerification(@RequestParam String token) {

        verificationService.verifyAccount(token);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Forgot password",
            description = "Sends an email to the user to reset the password"
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody
            ForgotPasswordRequest forgotPasswordRequest,
            HttpServletRequest httpServletRequest
    ) {

        passwordResetService.sendResetLink(forgotPasswordRequest.email(), httpServletRequest);

        return ResponseEntity
                .ok(
                        new MessageResponse("Password reset link has been sent to your email which is valid for 30 minutes. Please reset your password")
                );
    }

    @Operation(
            summary = "Reset password",
            description = "Allows to change the password of an user"
    )
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody
            ResetPasswordRequest resetPasswordRequest,
            HttpServletRequest httpServletRequest
    ) {

        passwordResetService.resetPassword(
                resetPasswordRequest.token(),
                resetPasswordRequest.newPassword(),
                httpServletRequest
        );

        return ResponseEntity
                .ok(
                        new MessageResponse("Your password has been changed successfully")
                );
    }
}


