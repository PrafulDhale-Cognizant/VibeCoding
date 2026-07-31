package com.simplifiedbilling.auth.service.impl;

import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.auth.dto.ChangePasswordRequest;
import com.simplifiedbilling.auth.dto.LoginRequest;
import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.auth.repository.UserAccountRepository;
import com.simplifiedbilling.auth.mapper.UserMapper;
import com.simplifiedbilling.auth.service.AuthenticationService;
import com.simplifiedbilling.auth.service.SessionService;
import com.simplifiedbilling.auth.service.UsernameNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.config.LoginSecurityProperties;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultAuthenticationService implements AuthenticationService {

    private static final String INVALID_CREDENTIALS = "Username or password is incorrect.";

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginSecurityProperties loginProperties;
    private final SessionService sessionService;
    private final AuditWriter auditWriter;
    private final Clock clock;
    private final String dummyPasswordHash;
    private final UsernameNormalizer usernameNormalizer;
    private final UserMapper userMapper;

    public DefaultAuthenticationService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            LoginSecurityProperties loginProperties,
            SessionService sessionService,
            AuditWriter auditWriter,
            Clock clock,
            UsernameNormalizer usernameNormalizer,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginProperties = loginProperties;
        this.sessionService = sessionService;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("TimingEqualization#Only123");
        this.usernameNormalizer = usernameNormalizer;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(noRollbackFor = AuthenticationFailureException.class)
    public AuthResponse login(LoginRequest request) {
        Instant now = Instant.now(clock);
        String username = usernameNormalizer.normalize(request.username());
        UserAccount user = userRepository.findByUsernameForUpdate(username).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new AuthenticationFailureException("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!user.isActive()) {
            throw new AuthenticationFailureException("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }
        if (user.isLockedAt(now)) {
            if (!passwordMatches) {
                throw new AuthenticationFailureException("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
            }
            throw new AuthenticationFailureException(
                    "ACCOUNT_TEMPORARILY_LOCKED",
                    "Too many failed attempts. Try again later.");
        }
        if (!passwordMatches) {
            user.recordFailedLogin(
                    now,
                    loginProperties.maxFailedAttempts(),
                    loginProperties.lockDuration());
            auditWriter.write(
                    user.getId(),
                    "AUTH_LOGIN_FAILED",
                    "USER",
                    user.getId(),
                    Map.of("username", username));
            throw new AuthenticationFailureException("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        user.recordSuccessfulLogin(now);
        return sessionService.createSession(user, "AUTH_LOGIN_SUCCEEDED");
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummary getCurrentUser(String userId) {
        return userMapper.toSummary(requireUser(userId));
    }

    @Override
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        UserAccount user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.UNAUTHORIZED,
                        "USER_NOT_FOUND",
                        "The signed-in user no longer exists."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "CURRENT_PASSWORD_INCORRECT",
                    "The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_UNCHANGED",
                    "The new password must be different from the current password.");
        }

        Instant now = Instant.now(clock);
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        sessionService.revokeAllForUser(userId);
        auditWriter.write(userId, "AUTH_PASSWORD_CHANGED", "USER", userId, Map.of());
    }

    private UserAccount requireUser(String userId) {
        return userRepository.findById(userId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.UNAUTHORIZED,
                        "USER_NOT_FOUND",
                        "The signed-in user no longer exists."));
    }

}
