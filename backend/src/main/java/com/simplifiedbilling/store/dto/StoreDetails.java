package com.simplifiedbilling.store.dto;

import com.simplifiedbilling.store.domain.ReceiptWidth;

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
        boolean logoAvailable,
        long version,
        Instant setupCompletedAt,
        Instant updatedAt) {
}
