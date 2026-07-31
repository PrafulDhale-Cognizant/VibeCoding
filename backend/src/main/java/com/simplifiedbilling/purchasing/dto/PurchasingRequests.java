package com.simplifiedbilling.purchasing.dto;

import com.simplifiedbilling.purchasing.domain.SupplierPaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PurchasingRequests {

    private PurchasingRequests() {
    }

    public record CreateSupplierRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 15) String gstin,
            @Size(max = 500) String address,
            @Size(max = 500) String notes) {
    }

    public record UpdateSupplierRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 15) String gstin,
            @Size(max = 500) String address,
            @Size(max = 500) String notes,
            boolean active,
            long version) {
    }

    public record ReceivePurchaseRequest(
            @NotBlank String supplierId,
            @Size(max = 80) String supplierInvoiceNumber,
            @NotNull LocalDate invoiceDate,
            boolean pricesIncludeTax,
            @NotEmpty @Size(max = 100) List<@Valid PurchaseItemRequest> items,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal amountPaid,
            SupplierPaymentMode paymentMode,
            @Size(max = 100) String paymentReference,
            @Size(max = 500) String notes) {
    }

    public record PurchaseItemRequest(
            @NotBlank String productId,
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 16, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal unitCost) {
    }

    public record SupplierPaymentRequest(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull SupplierPaymentMode paymentMode,
            @Size(max = 100) String reference,
            @Size(max = 500) String notes,
            long balanceVersion) {
    }
}
