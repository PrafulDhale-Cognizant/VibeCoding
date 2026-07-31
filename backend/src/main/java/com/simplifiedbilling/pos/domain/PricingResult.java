package com.simplifiedbilling.pos.domain;

import java.math.BigDecimal;
import java.util.List;

public record PricingResult(
        List<PricingLine> lines,
        TaxMode taxMode,
        boolean pricesIncludeGst,
        BigDecimal subtotalAmount,
        BigDecimal lineDiscountAmount,
        BigDecimal billDiscountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal roundOffAmount,
        BigDecimal totalAmount) {
}
