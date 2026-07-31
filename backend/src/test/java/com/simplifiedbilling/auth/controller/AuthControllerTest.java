package com.simplifiedbilling.auth.controller;

import com.simplifiedbilling.auth.domain.UserRole;
import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.auth.dto.ChangePasswordRequest;
import com.simplifiedbilling.auth.dto.LoginRequest;
import com.simplifiedbilling.auth.dto.LogoutRequest;
import com.simplifiedbilling.auth.dto.RefreshRequest;
import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.auth.service.AuthenticationService;
import com.simplifiedbilling.auth.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final AuthController controller = new AuthController(authenticationService, sessionService);

    @Test
    void delegatesEveryAuthenticationEndpoint() {
        LoginRequest login = new LoginRequest("admin", "password");
        RefreshRequest refresh = new RefreshRequest("refresh-token");
        LogoutRequest logout = new LogoutRequest("refresh-token");
        ChangePasswordRequest change = new ChangePasswordRequest("old-password", "NewPassword#123");
        AuthResponse response = response();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-1");
        when(authenticationService.login(login)).thenReturn(response);
        when(sessionService.rotate("refresh-token")).thenReturn(response);
        when(authenticationService.getCurrentUser("user-1")).thenReturn(response.user());

        assertThat(controller.login(login)).isSameAs(response);
        assertThat(controller.refresh(refresh)).isSameAs(response);
        controller.logout(logout);
        assertThat(controller.me(jwt)).isEqualTo(response.user());
        controller.changePassword(jwt, change);

        verify(sessionService).revoke("refresh-token");
        verify(authenticationService).changePassword("user-1", change);
    }

    private AuthResponse response() {
        UserSummary user = new UserSummary(
                "user-1",
                "admin",
                "Admin",
                Set.of(UserRole.ADMIN),
                true,
                null,
                0);
        return new AuthResponse(
                "Bearer",
                "access",
                Instant.now().plusSeconds(900),
                "refresh",
                Instant.now().plusSeconds(1800),
                user);
    }
}
