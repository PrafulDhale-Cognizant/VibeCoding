package com.simplifiedbilling.auth.dto;

import com.simplifiedbilling.auth.domain.UserRole;

import java.time.Instant;
import java.util.Set;

public record UserSummary(
        String id,
        String username,
        String displayName,
        Set<UserRole> roles,
        boolean active,
        Instant lastLoginAt,
        long version) {
}
