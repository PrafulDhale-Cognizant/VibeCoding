package com.simplifiedbilling.pos.domain;

import com.simplifiedbilling.inventory.service.SaleProductSnapshot;

import java.math.BigDecimal;

public record PricingLine(
        int lineNumber,
        SaleProductSnapshot product,
        BigDecimal quantity,
        BigDecimal grossAmount,
        BigDecimal lineDiscountAmount,
        BigDecimal billDiscountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal lineTotal) {
}
