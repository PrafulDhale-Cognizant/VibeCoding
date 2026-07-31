package com.simplifiedbilling.auth.controller;

import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.auth.dto.ChangePasswordRequest;
import com.simplifiedbilling.auth.dto.LoginRequest;
import com.simplifiedbilling.auth.dto.LogoutRequest;
import com.simplifiedbilling.auth.dto.RefreshRequest;
import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.auth.service.AuthenticationService;
import com.simplifiedbilling.auth.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final SessionService sessionService;

    public AuthController(
            AuthenticationService authenticationService,
            SessionService sessionService) {
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return sessionService.rotate(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        sessionService.revoke(request.refreshToken());
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal Jwt jwt) {
        return authenticationService.getCurrentUser(jwt.getSubject());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(jwt.getSubject(), request);
    }
}
