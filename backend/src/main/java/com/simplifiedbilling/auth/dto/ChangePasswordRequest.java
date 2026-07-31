package com.simplifiedbilling.auth.dto;

import com.simplifiedbilling.shared.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @StrongPassword String newPassword) {
}
