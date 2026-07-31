package com.simplifiedbilling.system.dto;

import java.time.Instant;

public record SystemHealthResponse(
        String status,
        String application,
        String version,
        String database,
        String javaVersion,
        Instant timestamp) {
}
