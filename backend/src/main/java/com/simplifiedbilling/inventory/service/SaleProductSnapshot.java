package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.domain.ProductUnit;

import java.math.BigDecimal;

public record SaleProductSnapshot(
        String productId,
        String name,
        String receiptName,
        String barcode,
        ProductUnit unit,
        BigDecimal gstRate,
        BigDecimal purchaseCost,
        BigDecimal sellingPrice,
        BigDecimal availableQuantity) {
}
