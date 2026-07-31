package com.simplifiedbilling.inventory.service;

import java.math.BigDecimal;

public record PurchaseReturnStockRequest(
        String productId,
        String productName,
        BigDecimal quantity) {
}
