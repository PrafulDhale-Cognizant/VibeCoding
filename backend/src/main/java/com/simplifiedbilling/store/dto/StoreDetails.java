package com.simplifiedbilling.store.dto;

import com.simplifiedbilling.store.domain.ReceiptWidth;
import com.simplifiedbilling.store.domain.A4InvoiceTemplate;
import com.simplifiedbilling.store.domain.ThermalReceiptTemplate;

import java.time.Instant;

public record StoreDetails(
        String shopName,
        String ownerName,
        String addressLine1,
        String addressLine2,
        String city,
        String stateName,
        String stateCode,
        String postalCode,
        String phone,
        String email,
        boolean gstRegistered,
        String gstin,
        String currencyCode,
        String timezone,
        String invoicePrefix,
        int financialYearStartMonth,
        ReceiptWidth receiptWidth,
        A4InvoiceTemplate a4InvoiceTemplate,
        ThermalReceiptTemplate thermalReceiptTemplate,
        boolean logoAvailable,
        long version,
        Instant setupCompletedAt,
        Instant updatedAt) {
}
