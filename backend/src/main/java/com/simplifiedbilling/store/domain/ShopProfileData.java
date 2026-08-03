package com.simplifiedbilling.store.domain;

public record ShopProfileData(
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
        InvoicePrintFormat invoicePrintFormat,
        A4InvoiceTemplate a4InvoiceTemplate,
        ThermalReceiptTemplate thermalReceiptTemplate) {
}
