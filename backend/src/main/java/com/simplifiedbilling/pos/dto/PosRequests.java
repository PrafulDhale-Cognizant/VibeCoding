package com.simplifiedbilling.pos.dto;

import com.simplifiedbilling.pos.domain.DiscountType;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.domain.TaxMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class PosRequests {

    private PosRequests() {
    }

    public record CartItemRequest(
            @NotBlank String productId,
            @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
            DiscountType discountType,
            @DecimalMin("0.00") BigDecimal discountValue) {
    }

    public record QuoteRequest(
            @NotEmpty @Valid List<CartItemRequest> items,
            DiscountType billDiscountType,
            @DecimalMin("0.00") BigDecimal billDiscountValue,
            @NotNull TaxMode taxMode,
            @Size(max = 15) @Pattern(
                    regexp = "^$|[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]",
                    message = "Enter a valid customer GSTIN in uppercase.")
            String customerGstin) {
    }

    public record PaymentRequest(
            @NotNull PaymentMode mode,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @DecimalMin("0.00") BigDecimal tenderedAmount,
            @Size(max = 100) String reference,
            @Size(max = 36) String customerId) {
    }

    public record CheckoutRequest(
            @NotEmpty @Valid List<CartItemRequest> items,
            DiscountType billDiscountType,
            @DecimalMin("0.00") BigDecimal billDiscountValue,
            @NotNull TaxMode taxMode,
            @Size(max = 15) @Pattern(
                    regexp = "^$|[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]",
                    message = "Enter a valid customer GSTIN in uppercase.")
            String customerGstin,
            @NotEmpty @Valid List<PaymentRequest> payments,
            @Size(max = 500) String notes) {

        public QuoteRequest quoteRequest() {
            return new QuoteRequest(items, billDiscountType, billDiscountValue, taxMode, customerGstin);
        }
    }
}
