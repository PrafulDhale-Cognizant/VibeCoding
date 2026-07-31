package com.simplifiedbilling.inventory.service;

import java.math.BigDecimal;

public record PurchaseStockRequest(
        String productId,
        BigDecimal quantity,
        BigDecimal unitCost) {
}
