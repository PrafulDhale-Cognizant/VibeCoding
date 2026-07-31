package com.simplifiedbilling.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        boolean active,
        @PositiveOrZero long version) {
}
