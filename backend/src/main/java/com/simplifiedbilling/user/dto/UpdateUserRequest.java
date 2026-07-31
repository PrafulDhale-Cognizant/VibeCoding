package com.simplifiedbilling.user.dto;

import com.simplifiedbilling.auth.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotEmpty Set<UserRole> roles,
        @NotNull Boolean active,
        @NotNull Long version) {
}
