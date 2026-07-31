package com.simplifiedbilling.user.service.impl;

import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.domain.UserRole;
import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.auth.repository.UserAccountRepository;
import com.simplifiedbilling.auth.mapper.UserMapper;
import com.simplifiedbilling.auth.service.SessionService;
import com.simplifiedbilling.auth.service.UsernameNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.user.dto.CreateUserRequest;
import com.simplifiedbilling.user.dto.ResetPasswordRequest;
import com.simplifiedbilling.user.dto.UpdateUserRequest;
import com.simplifiedbilling.user.service.UserManagementService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class DefaultUserManagementService implements UserManagementService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuditWriter auditWriter;
    private final Clock clock;
    private final UsernameNormalizer usernameNormalizer;
    private final UserMapper userMapper;

    public DefaultUserManagementService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionService sessionService,
            AuditWriter auditWriter,
            Clock clock,
            UsernameNormalizer usernameNormalizer,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.usernameNormalizer = usernameNormalizer;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummary> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "displayName"))
                .stream()
                .map(userMapper::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public UserSummary createUser(
            String actorUserId,
            boolean actorIsOwner,
            CreateUserRequest request) {

        verifyOwnerRoleAssignment(actorIsOwner, request.roles().contains(UserRole.OWNER));
        String username = usernameNormalizer.normalize(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "USERNAME_ALREADY_EXISTS",
                    "That username is already in use.");
        }

        UserAccount user = UserAccount.create(
                username,
                request.displayName().trim(),
                passwordEncoder.encode(request.password()),
                request.roles(),
                Instant.now(clock));
        userRepository.save(user);
        auditWriter.write(
                actorUserId,
                "USER_CREATED",
                "USER",
                user.getId(),
                Map.of("username", username, "roles", request.roles()));
        return userMapper.toSummary(user);
    }

    @Override
    @Transactional
    public UserSummary updateUser(
            String actorUserId,
            boolean actorIsOwner,
            String targetUserId,
            UpdateUserRequest request) {

        UserAccount target = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> userNotFound(targetUserId));
        if (target.getVersion() != request.version()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "STALE_USER_VERSION",
                    "This user was changed by another request. Refresh and try again.");
        }

        boolean wasOwner = target.getRoles().contains(UserRole.OWNER);
        boolean willBeOwner = request.roles().contains(UserRole.OWNER);
        verifyOwnerRoleAssignment(actorIsOwner, wasOwner || willBeOwner);

        if (actorUserId.equals(targetUserId) && !request.active()) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "CANNOT_DEACTIVATE_SELF",
                    "You cannot deactivate your own account.");
        }
        if (wasOwner && (!willBeOwner || !request.active())
                && userRepository.countActiveUsersWithRole(UserRole.OWNER) <= 1) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "LAST_OWNER_REQUIRED",
                    "At least one active owner account is required.");
        }

        target.updateProfile(
                request.displayName().trim(),
                request.roles(),
                request.active(),
                Instant.now(clock));
        if (!request.active()) {
            sessionService.revokeAllForUser(targetUserId);
        }
        userRepository.flush();
        auditWriter.write(
                actorUserId,
                "USER_UPDATED",
                "USER",
                targetUserId,
                Map.of("active", request.active(), "roles", request.roles()));
        return userMapper.toSummary(target);
    }

    @Override
    @Transactional
    public void resetPassword(
            String actorUserId,
            boolean actorIsOwner,
            String targetUserId,
            ResetPasswordRequest request) {

        UserAccount target = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> userNotFound(targetUserId));
        verifyOwnerRoleAssignment(actorIsOwner, target.getRoles().contains(UserRole.OWNER));

        target.changePassword(passwordEncoder.encode(request.newPassword()), Instant.now(clock));
        sessionService.revokeAllForUser(targetUserId);
        auditWriter.write(
                actorUserId,
                "USER_PASSWORD_RESET",
                "USER",
                targetUserId,
                Map.of());
    }

    private void verifyOwnerRoleAssignment(boolean actorIsOwner, boolean operationTouchesOwner) {
        if (operationTouchesOwner && !actorIsOwner) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    "OWNER_PERMISSION_REQUIRED",
                    "Only an owner can create or modify an owner account.");
        }
    }

    private ApplicationException userNotFound(String userId) {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User " + userId + " was not found.");
    }
}
