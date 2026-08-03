package com.simplifiedbilling.store.dto;

import com.simplifiedbilling.store.domain.ReceiptWidth;
import com.simplifiedbilling.store.domain.A4InvoiceTemplate;
import com.simplifiedbilling.store.domain.InvoicePrintFormat;
import com.simplifiedbilling.store.domain.ThermalReceiptTemplate;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreProfileRequest(
        @NotBlank @Size(max = 150) String shopName,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String stateName,
        @NotBlank @Pattern(regexp = "[0-9]{2}", message = "State code must contain two digits.")
        String stateCode,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{5}", message = "Enter a valid six-digit PIN code.")
        String postalCode,
        @NotBlank @Pattern(regexp = "[0-9+() -]{7,20}", message = "Enter a valid phone number.")
        String phone,
        @Email @Size(max = 254) String email,
        boolean gstRegistered,
        @Pattern(
                regexp = "^$|[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]",
                message = "Enter a valid GSTIN in uppercase.")
        String gstin,
        @NotBlank @Pattern(regexp = "INR", message = "Only INR is currently supported.")
        String currencyCode,
        @NotBlank @Pattern(regexp = "Asia/Kolkata", message = "Only Asia/Kolkata is currently supported.")
        String timezone,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,12}", message = "Use letters, digits, or hyphens.")
        String invoicePrefix,
        @Min(1) @Max(12) int financialYearStartMonth,
        @NotNull ReceiptWidth receiptWidth,
        InvoicePrintFormat invoicePrintFormat,
        A4InvoiceTemplate a4InvoiceTemplate,
        ThermalReceiptTemplate thermalReceiptTemplate) {

    @AssertTrue(message = "GSTIN is required when the shop is GST registered.")
    public boolean isGstinConsistent() {
        return !gstRegistered || (gstin != null && !gstin.isBlank());
    }
}
