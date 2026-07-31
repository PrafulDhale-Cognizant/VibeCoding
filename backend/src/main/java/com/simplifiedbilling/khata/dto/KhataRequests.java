package com.simplifiedbilling.khata.dto;

import com.simplifiedbilling.khata.domain.SettlementMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class KhataRequests {

    private KhataRequests() {
    }

    public record CreateCustomerRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 24) String phone,
            @Size(max = 500) String notes) {
    }

    public record UpdateCustomerRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 24) String phone,
            @Size(max = 500) String notes,
            boolean active,
            long version) {
    }

    public record SettlementRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull SettlementMode paymentMode,
            @Size(max = 100) String reference,
            @Size(max = 500) String notes,
            long balanceVersion) {
    }
}
