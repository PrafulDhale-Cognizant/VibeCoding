package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.inventory.service.PurchaseProductSnapshot;

import java.math.BigDecimal;

public record PurchasePricingLine(
        int lineNumber,
        PurchaseProductSnapshot product,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {
}
