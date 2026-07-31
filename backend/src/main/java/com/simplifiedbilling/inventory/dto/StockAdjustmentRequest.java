package com.simplifiedbilling.inventory.dto;

import com.simplifiedbilling.inventory.domain.StockReasonCode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockAdjustmentRequest(
        @NotNull @Digits(integer = 16, fraction = 3) BigDecimal quantityDelta,
        @NotNull StockReasonCode reasonCode,
        @Size(max = 500) String notes,
        @PositiveOrZero long stockVersion) {
}
