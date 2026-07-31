package com.simplifiedbilling.auth.service.impl;

import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.domain.UserRole;
import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.auth.dto.ChangePasswordRequest;
import com.simplifiedbilling.auth.dto.LoginRequest;
import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.auth.mapper.UserMapper;
import com.simplifiedbilling.auth.repository.UserAccountRepository;
import com.simplifiedbilling.auth.service.SessionService;
import com.simplifiedbilling.auth.service.UsernameNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.config.LoginSecurityProperties;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T09:00:00Z");

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditWriter auditWriter;

    private DefaultAuthenticationService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("TimingEqualization#Only123")).thenReturn("dummy-hash");
        service = new DefaultAuthenticationService(
                userRepository,
                passwordEncoder,
                new LoginSecurityProperties(5, Duration.ofMinutes(15)),
                sessionService,
                auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new UsernameNormalizer(),
                new UserMapper());
    }

    @Test
    void rejectsUnknownUserWithTimingEqualization() {
        when(userRepository.findByUsernameForUpdate("missing")).thenReturn(Optional.empty());

        assertAuthError(
                () -> service.login(new LoginRequest(" Missing ", "wrong")),
                "INVALID_CREDENTIALS");

        verify(passwordEncoder).matches("wrong", "dummy-hash");
    }

    @Test
    void rejectsInactiveAndLockedUsersWithoutLeakingWrongPassword() {
        UserAccount inactive = user("inactive");
        inactive.updateProfile("Inactive", Set.of(UserRole.CASHIER), false, NOW.minusSeconds(5));
        when(userRepository.findByUsernameForUpdate("inactive")).thenReturn(Optional.of(inactive));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);
        assertAuthError(
                () -> service.login(new LoginRequest("inactive", "password")),
                "INVALID_CREDENTIALS");

        UserAccount locked = user("locked");
        locked.recordFailedLogin(NOW.minusSeconds(5), 1, Duration.ofMinutes(15));
        when(userRepository.findByUsernameForUpdate("locked")).thenReturn(Optional.of(locked));
        when(passwordEncoder.matches("correct", "stored-hash")).thenReturn(true);
        assertAuthError(
                () -> service.login(new LoginRequest("locked", "correct")),
                "ACCOUNT_TEMPORARILY_LOCKED");

        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);
        assertAuthError(
                () -> service.login(new LoginRequest("locked", "wrong")),
                "INVALID_CREDENTIALS");
    }

    @Test
    void recordsFailedLoginAndCreatesSessionAfterSuccess() {
        UserAccount user = user("cashier");
        when(userRepository.findByUsernameForUpdate("cashier")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "stored-hash")).thenReturn(false);

        assertAuthError(
                () -> service.login(new LoginRequest("cashier", "bad")),
                "INVALID_CREDENTIALS");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(auditWriter).write(
                org.mockito.ArgumentMatchers.eq(user.getId()),
                org.mockito.ArgumentMatchers.eq("AUTH_LOGIN_FAILED"),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(user.getId()),
                org.mockito.ArgumentMatchers.any());

        when(passwordEncoder.matches("good", "stored-hash")).thenReturn(true);
        AuthResponse expected = response(user);
        when(sessionService.createSession(user, "AUTH_LOGIN_SUCCEEDED")).thenReturn(expected);

        assertThat(service.login(new LoginRequest(" CASHIER ", "good"))).isSameAs(expected);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void returnsOnlyActiveCurrentUser() {
        UserAccount user = user("cashier");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(service.getCurrentUser(user.getId()).username()).isEqualTo("cashier");

        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        assertApplicationError(
                () -> service.getCurrentUser("missing"),
                HttpStatus.UNAUTHORIZED,
                "USER_NOT_FOUND");

        user.updateProfile("Cashier", Set.of(UserRole.CASHIER), false, NOW);
        assertApplicationError(
                () -> service.getCurrentUser(user.getId()),
                HttpStatus.UNAUTHORIZED,
                "USER_NOT_FOUND");
    }

    @Test
    void changesPasswordAndRevokesSessions() {
        UserAccount user = user("cashier");
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "stored-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword#123", "stored-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword#123")).thenReturn("new-hash");

        service.changePassword(
                user.getId(),
                new ChangePasswordRequest("current", "NewPassword#123"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
        verify(sessionService).revokeAllForUser(user.getId());
        verify(auditWriter).write(
                user.getId(),
                "AUTH_PASSWORD_CHANGED",
                "USER",
                user.getId(),
                java.util.Map.of());
    }

    @Test
    void rejectsInvalidPasswordChangesAndMissingUser() {
        when(userRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        assertApplicationError(
                () -> service.changePassword(
                        "missing",
                        new ChangePasswordRequest("current", "NewPassword#123")),
                HttpStatus.UNAUTHORIZED,
                "USER_NOT_FOUND");

        UserAccount user = user("cashier");
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);
        assertApplicationError(
                () -> service.changePassword(
                        user.getId(),
                        new ChangePasswordRequest("wrong", "NewPassword#123")),
                HttpStatus.BAD_REQUEST,
                "CURRENT_PASSWORD_INCORRECT");

        when(passwordEncoder.matches("current", "stored-hash")).thenReturn(true);
        when(passwordEncoder.matches("SamePassword#123", "stored-hash")).thenReturn(true);
        assertApplicationError(
                () -> service.changePassword(
                        user.getId(),
                        new ChangePasswordRequest("current", "SamePassword#123")),
                HttpStatus.BAD_REQUEST,
                "PASSWORD_UNCHANGED");
        verify(sessionService, never()).revokeAllForUser("missing");
    }

    private UserAccount user(String username) {
        return UserAccount.create(
                username,
                "Cashier",
                "stored-hash",
                Set.of(UserRole.CASHIER),
                NOW.minusSeconds(60));
    }

    private AuthResponse response(UserAccount user) {
        return new AuthResponse(
                "Bearer",
                "access",
                NOW.plusSeconds(900),
                "refresh",
                NOW.plus(Duration.ofDays(7)),
                new UserMapper().toSummary(user));
    }

    private void assertAuthError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String code) {
        assertApplicationError(callable, HttpStatus.UNAUTHORIZED, code);
    }

    private void assertApplicationError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            HttpStatus status,
            String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(status);
                    assertThat(exception.getCode()).isEqualTo(code);
                });
    }
}
