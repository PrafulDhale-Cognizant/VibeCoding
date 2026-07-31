package com.simplifiedbilling.auth.service.impl;

import com.simplifiedbilling.auth.domain.RefreshToken;
import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.domain.UserRole;
import com.simplifiedbilling.auth.mapper.UserMapper;
import com.simplifiedbilling.auth.repository.RefreshTokenRepository;
import com.simplifiedbilling.auth.service.JwtTokenService;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.config.JwtProperties;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T09:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private AuditWriter auditWriter;

    private DefaultSessionService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSessionService(
                refreshTokenRepository,
                jwtTokenService,
                new JwtProperties(
                        "test",
                        "unused",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)),
                auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new UserMapper());
    }

    @Test
    void createsSessionWithHashedRefreshToken() {
        UserAccount user = user(true);
        when(jwtTokenService.issue(user)).thenReturn(access());
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createSession(user, "AUTH_LOGIN_SUCCEEDED");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank().doesNotContain("=");
        assertThat(response.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash())
                .hasSize(64)
                .isNotEqualTo(response.refreshToken());
        verify(auditWriter).write(
                user.getId(),
                "AUTH_LOGIN_SUCCEEDED",
                "USER",
                user.getId(),
                java.util.Map.of());
    }

    @Test
    void rotatesUsableTokenAndRevokesOriginal() {
        UserAccount user = user(true);
        RefreshToken current = RefreshToken.create(
                user,
                "stored-hash",
                NOW.minusSeconds(5),
                NOW.plusSeconds(300));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(current));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenService.issue(user)).thenReturn(access());

        var response = service.rotate("raw-refresh-token");

        assertThat(response.refreshToken()).isNotEqualTo("raw-refresh-token");
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        verify(auditWriter).write(
                user.getId(),
                "AUTH_TOKEN_REFRESHED",
                "USER",
                user.getId(),
                java.util.Map.of());
    }

    @Test
    void rejectsMissingExpiredRevokedAndInactiveTokens() {
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.empty());
        assertInvalidRefresh(() -> service.rotate("missing"));

        UserAccount active = user(true);
        RefreshToken expired = RefreshToken.create(
                active,
                "expired",
                NOW.minusSeconds(100),
                NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(expired));
        assertInvalidRefresh(() -> service.rotate("expired"));

        RefreshToken revoked = RefreshToken.create(
                active,
                "revoked",
                NOW.minusSeconds(100),
                NOW.plusSeconds(100));
        revoked.revoke(NOW.minusSeconds(1), null);
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(revoked));
        assertInvalidRefresh(() -> service.rotate("revoked"));

        UserAccount inactive = user(false);
        RefreshToken inactiveToken = RefreshToken.create(
                inactive,
                "inactive",
                NOW.minusSeconds(1),
                NOW.plusSeconds(100));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(inactiveToken));
        assertInvalidRefresh(() -> service.rotate("inactive"));
    }

    @Test
    void revokesExistingTokenIdempotentlyAndIgnoresMissingToken() {
        UserAccount user = user(true);
        RefreshToken token = RefreshToken.create(
                user,
                "hash",
                NOW.minusSeconds(1),
                NOW.plusSeconds(100));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(token));

        service.revoke("raw");
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        verify(auditWriter).write(
                user.getId(),
                "AUTH_LOGOUT",
                "USER",
                user.getId(),
                java.util.Map.of());

        service.revoke("raw");

        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.empty());
        service.revoke("missing");
    }

    @Test
    void revokesAllUserSessions() {
        when(refreshTokenRepository.revokeAllActiveForUser("user-1", NOW)).thenReturn(2);

        service.revokeAllForUser("user-1");

        verify(refreshTokenRepository).revokeAllActiveForUser("user-1", NOW);
        verify(auditWriter, never()).write(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private UserAccount user(boolean active) {
        UserAccount user = UserAccount.create(
                active ? "active" : "inactive",
                "User",
                "hash",
                Set.of(UserRole.CASHIER),
                NOW.minusSeconds(60));
        if (!active) {
            user.updateProfile("User", Set.of(UserRole.CASHIER), false, NOW.minusSeconds(5));
        }
        return user;
    }

    private JwtTokenService.IssuedAccessToken access() {
        return new JwtTokenService.IssuedAccessToken("access-token", NOW.plusSeconds(900));
    }

    private void assertInvalidRefresh(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getCode()).isEqualTo("INVALID_REFRESH_TOKEN");
                });
    }
}
