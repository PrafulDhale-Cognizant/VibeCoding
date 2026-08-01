package com.simplifiedbilling.pos.dto;

import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.domain.ReturnDisposition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class SaleReturnRequests {
    private SaleReturnRequests() { }

    public record LineRequest(
            @NotBlank String invoiceItemId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @NotNull ReturnDisposition disposition) { }

    public record RefundRequest(
            @NotNull PaymentMode mode,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 100) String reference,
            @Size(max = 36) String customerId) { }

    public record CreateRequest(
            @NotEmpty @Valid List<LineRequest> items,
            @NotEmpty @Valid List<RefundRequest> refunds,
            @NotBlank @Size(max = 500) String reason) { }

    public record CancellationRequest(
            @NotEmpty @Valid List<RefundRequest> refunds,
            @NotBlank @Size(max = 500) String reason) { }
}
