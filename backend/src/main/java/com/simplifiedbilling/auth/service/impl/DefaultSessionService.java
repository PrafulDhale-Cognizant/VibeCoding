package com.simplifiedbilling.auth.service.impl;

import com.simplifiedbilling.auth.domain.RefreshToken;
import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.auth.repository.RefreshTokenRepository;
import com.simplifiedbilling.auth.mapper.UserMapper;
import com.simplifiedbilling.auth.service.JwtTokenService;
import com.simplifiedbilling.auth.service.SessionService;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.config.JwtProperties;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Service
public class DefaultSessionService implements SessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final AuditWriter auditWriter;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    private final UserMapper userMapper;

    public DefaultSessionService(
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenService jwtTokenService,
            JwtProperties jwtProperties,
            AuditWriter auditWriter,
            Clock clock,
            UserMapper userMapper) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public AuthResponse createSession(UserAccount user, String eventType) {
        Instant now = Instant.now(clock);
        return createAndPersist(user, now, eventType);
    }

    @Override
    @Transactional
    public AuthResponse rotate(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(this::invalidRefreshToken);

        if (!current.isUsableAt(now) || !current.getUser().isActive()) {
            throw invalidRefreshToken();
        }

        GeneratedRefreshToken replacementValue = generateRefreshToken(current.getUser(), now);
        refreshTokenRepository.save(replacementValue.entity());
        current.revoke(now, replacementValue.entity().getId());
        auditWriter.write(
                current.getUser().getId(),
                "AUTH_TOKEN_REFRESHED",
                "USER",
                current.getUser().getId(),
                Map.of());

        JwtTokenService.IssuedAccessToken access = jwtTokenService.issue(current.getUser());
        return toResponse(current.getUser(), access, replacementValue);
    }

    @Override
    @Transactional
    public void revoke(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.revoke(now, null);
                        auditWriter.write(
                                token.getUser().getId(),
                                "AUTH_LOGOUT",
                                "USER",
                                token.getUser().getId(),
                                Map.of());
                    }
                });
    }

    @Override
    @Transactional
    public void revokeAllForUser(String userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now(clock));
    }

    private AuthResponse createAndPersist(UserAccount user, Instant now, String eventType) {
        GeneratedRefreshToken refresh = generateRefreshToken(user, now);
        refreshTokenRepository.save(refresh.entity());
        JwtTokenService.IssuedAccessToken access = jwtTokenService.issue(user);
        auditWriter.write(user.getId(), eventType, "USER", user.getId(), Map.of());
        return toResponse(user, access, refresh);
    }

    private GeneratedRefreshToken generateRefreshToken(UserAccount user, Instant now) {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken entity = RefreshToken.create(
                user,
                hash(rawValue),
                now,
                now.plus(jwtProperties.refreshTokenTtl()));
        return new GeneratedRefreshToken(rawValue, entity);
    }

    private AuthResponse toResponse(
            UserAccount user,
            JwtTokenService.IssuedAccessToken access,
            GeneratedRefreshToken refresh) {

        return new AuthResponse(
                "Bearer",
                access.value(),
                access.expiresAt(),
                refresh.rawValue(),
                refresh.entity().getExpiresAt(),
                userMapper.toSummary(user));
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private ApplicationException invalidRefreshToken() {
        return new ApplicationException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                "The session has expired. Sign in again.");
    }

    private record GeneratedRefreshToken(String rawValue, RefreshToken entity) {
    }
}
