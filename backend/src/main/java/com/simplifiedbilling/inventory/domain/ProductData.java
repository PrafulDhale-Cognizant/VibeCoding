package com.simplifiedbilling.inventory.domain;

import java.math.BigDecimal;

public record ProductData(
        String name,
        String receiptName,
        String sku,
        ProductUnit unit,
        String hsnCode,
        BigDecimal gstRate,
        BigDecimal purchaseCost,
        BigDecimal sellingPrice,
        BigDecimal minimumStockLevel,
        boolean active) {
}
