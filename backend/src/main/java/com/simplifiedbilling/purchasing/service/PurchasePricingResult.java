package com.simplifiedbilling.purchasing.service;

import java.math.BigDecimal;
import java.util.List;

public record PurchasePricingResult(
        List<PurchasePricingLine> lines,
        boolean pricesIncludeTax,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount) {

    public PurchasePricingResult {
        lines = List.copyOf(lines);
    }
}
