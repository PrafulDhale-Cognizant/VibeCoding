package com.simplifiedbilling.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "billing.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String secretBase64,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}
