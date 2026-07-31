package com.simplifiedbilling.setup.dto;

import com.simplifiedbilling.store.dto.StoreProfileRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record InitialSetupRequest(
        @NotNull @Valid StoreProfileRequest store,
        @NotNull @Valid OwnerSetupRequest owner,
        boolean dataResponsibilityAccepted) {

    @AssertTrue(message = "Data responsibility acknowledgement is required.")
    public boolean isDataResponsibilityAccepted() {
        return dataResponsibilityAccepted;
    }
}
