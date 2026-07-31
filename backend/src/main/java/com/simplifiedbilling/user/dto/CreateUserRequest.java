package com.simplifiedbilling.user.dto;

import com.simplifiedbilling.auth.domain.UserRole;
import com.simplifiedbilling.shared.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank
        @Pattern(
                regexp = "[A-Za-z0-9._-]{3,60}",
                message = "Username must be 3-60 characters using letters, digits, dot, underscore, or hyphen.")
        String username,
        @NotBlank @Size(max = 120) String displayName,
        @StrongPassword String password,
        @NotEmpty Set<UserRole> roles) {
}
