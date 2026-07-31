package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.domain.ProductUnit;

import java.math.BigDecimal;

public record PurchaseProductSnapshot(
        String productId,
        String name,
        ProductUnit unit,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal gstRate) {
}
