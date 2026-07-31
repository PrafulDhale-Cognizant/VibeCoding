package com.simplifiedbilling.user.service.impl;

import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.domain.UserRole;
import com.simplifiedbilling.auth.mapper.UserMapper;
import com.simplifiedbilling.auth.repository.UserAccountRepository;
import com.simplifiedbilling.auth.service.SessionService;
import com.simplifiedbilling.auth.service.UsernameNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.user.dto.CreateUserRequest;
import com.simplifiedbilling.user.dto.ResetPasswordRequest;
import com.simplifiedbilling.user.dto.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUserManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T09:00:00Z");

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditWriter auditWriter;

    private DefaultUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new DefaultUserManagementService(
                userRepository,
                passwordEncoder,
                sessionService,
                auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new UsernameNormalizer(),
                new UserMapper());
    }

    @Test
    void listsAndCreatesNormalizedUser() {
        UserAccount existing = user("viewer", Set.of(UserRole.VIEWER));
        when(userRepository.findAll(any(Sort.class))).thenReturn(List.of(existing));
        assertThat(service.listUsers()).extracting("username").containsExactly("viewer");

        when(userRepository.existsByUsername("cashier.one")).thenReturn(false);
        when(passwordEncoder.encode("StrongLocal#123")).thenReturn("encoded");
        when(userRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.createUser(
                "owner-1",
                false,
                new CreateUserRequest(
                        " Cashier.One ",
                        " Cashier One ",
                        "StrongLocal#123",
                        Set.of(UserRole.CASHIER)));

        assertThat(created.username()).isEqualTo("cashier.one");
        assertThat(created.displayName()).isEqualTo("Cashier One");
        assertThat(created.roles()).containsExactly(UserRole.CASHIER);
        verify(auditWriter).write(
                org.mockito.ArgumentMatchers.eq("owner-1"),
                org.mockito.ArgumentMatchers.eq("USER_CREATED"),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(created.id()),
                any());
    }

    @Test
    void rejectsDuplicateUsernameAndUnauthorizedOwnerCreation() {
        CreateUserRequest ownerRequest = new CreateUserRequest(
                "owner2",
                "Owner Two",
                "StrongLocal#123",
                Set.of(UserRole.OWNER));

        assertError(
                () -> service.createUser("admin", false, ownerRequest),
                HttpStatus.FORBIDDEN,
                "OWNER_PERMISSION_REQUIRED");

        when(userRepository.existsByUsername("cashier")).thenReturn(true);
        assertError(
                () -> service.createUser(
                        "owner",
                        true,
                        new CreateUserRequest(
                                "cashier",
                                "Cashier",
                                "StrongLocal#123",
                                Set.of(UserRole.CASHIER))),
                HttpStatus.CONFLICT,
                "USERNAME_ALREADY_EXISTS");
    }

    @Test
    void updatesUserAndRevokesSessionsWhenDeactivated() {
        UserAccount target = user("cashier", Set.of(UserRole.CASHIER));
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));

        var updated = service.updateUser(
                "owner",
                true,
                target.getId(),
                new UpdateUserRequest(
                        "Senior Cashier",
                        Set.of(UserRole.CASHIER, UserRole.VIEWER),
                        false,
                        0L));

        assertThat(updated.displayName()).isEqualTo("Senior Cashier");
        assertThat(updated.active()).isFalse();
        verify(sessionService).revokeAllForUser(target.getId());
        verify(userRepository).flush();
    }

    @Test
    void rejectsMissingStaleAndSelfDeactivationUpdates() {
        when(userRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        assertError(
                () -> service.updateUser(
                        "owner",
                        true,
                        "missing",
                        new UpdateUserRequest("Name", Set.of(UserRole.VIEWER), true, 0L)),
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND");

        UserAccount target = user("cashier", Set.of(UserRole.CASHIER));
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        assertError(
                () -> service.updateUser(
                        "owner",
                        true,
                        target.getId(),
                        new UpdateUserRequest("Name", Set.of(UserRole.CASHIER), true, 9L)),
                HttpStatus.CONFLICT,
                "STALE_USER_VERSION");

        assertError(
                () -> service.updateUser(
                        target.getId(),
                        true,
                        target.getId(),
                        new UpdateUserRequest("Name", Set.of(UserRole.CASHIER), false, 0L)),
                HttpStatus.BAD_REQUEST,
                "CANNOT_DEACTIVATE_SELF");
    }

    @Test
    void protectsOwnerAccountsAndLastOwner() {
        UserAccount owner = user("owner", Set.of(UserRole.OWNER, UserRole.ADMIN));
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));

        assertError(
                () -> service.updateUser(
                        "admin",
                        false,
                        owner.getId(),
                        new UpdateUserRequest(
                                "Owner",
                                Set.of(UserRole.OWNER),
                                true,
                                0L)),
                HttpStatus.FORBIDDEN,
                "OWNER_PERMISSION_REQUIRED");

        when(userRepository.countActiveUsersWithRole(UserRole.OWNER)).thenReturn(1L);
        assertError(
                () -> service.updateUser(
                        "other-owner",
                        true,
                        owner.getId(),
                        new UpdateUserRequest(
                                "Owner",
                                Set.of(UserRole.ADMIN),
                                true,
                                0L)),
                HttpStatus.CONFLICT,
                "LAST_OWNER_REQUIRED");
    }

    @Test
    void resetsPasswordAndProtectsOwnerReset() {
        UserAccount cashier = user("cashier", Set.of(UserRole.CASHIER));
        when(userRepository.findByIdForUpdate(cashier.getId())).thenReturn(Optional.of(cashier));
        when(passwordEncoder.encode("Replacement#123")).thenReturn("replacement");

        service.resetPassword(
                "admin",
                false,
                cashier.getId(),
                new ResetPasswordRequest("Replacement#123"));

        verify(sessionService).revokeAllForUser(cashier.getId());
        verify(auditWriter).write(
                "admin",
                "USER_PASSWORD_RESET",
                "USER",
                cashier.getId(),
                java.util.Map.of());

        UserAccount owner = user("owner", Set.of(UserRole.OWNER));
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        assertError(
                () -> service.resetPassword(
                        "admin",
                        false,
                        owner.getId(),
                        new ResetPasswordRequest("Replacement#123")),
                HttpStatus.FORBIDDEN,
                "OWNER_PERMISSION_REQUIRED");

        when(userRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        assertError(
                () -> service.resetPassword(
                        "owner",
                        true,
                        "missing",
                        new ResetPasswordRequest("Replacement#123")),
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND");
        verify(passwordEncoder, never()).encode("unused");
    }

    private UserAccount user(String username, Set<UserRole> roles) {
        return UserAccount.create(username, username, "hash", roles, NOW.minusSeconds(60));
    }

    private void assertError(
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
