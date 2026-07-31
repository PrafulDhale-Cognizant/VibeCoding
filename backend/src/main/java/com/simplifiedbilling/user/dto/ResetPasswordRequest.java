package com.simplifiedbilling.user.dto;

import com.simplifiedbilling.shared.validation.StrongPassword;

public record ResetPasswordRequest(@StrongPassword String newPassword) {
}
