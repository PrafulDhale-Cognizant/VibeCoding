package com.simplifiedbilling.system;

import java.time.Instant;

public record SystemHealthResponse(
        String status,
        String application,
        String version,
        String database,
        String javaVersion,
        Instant timestamp) {
}

