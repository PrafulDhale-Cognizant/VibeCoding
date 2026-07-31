package com.simplifiedbilling.inventory.dto;

import java.time.Instant;

public record CategoryResponse(
        String id,
        String name,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
