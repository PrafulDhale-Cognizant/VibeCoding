package com.simplifiedbilling.shared.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldViolation> fieldErrors,
        Instant timestamp,
        String path,
        String correlationId) {
}

