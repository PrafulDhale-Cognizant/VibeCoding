package com.simplifiedbilling.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateStoreRequest(
        @NotNull @Valid StoreProfileRequest profile,
        @NotNull Long version) {
}
