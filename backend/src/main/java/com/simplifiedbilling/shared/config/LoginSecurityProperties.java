package com.simplifiedbilling.shared.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "billing.security.login")
public record LoginSecurityProperties(
        @Min(1) int maxFailedAttempts,
        @NotNull Duration lockDuration) {
}
