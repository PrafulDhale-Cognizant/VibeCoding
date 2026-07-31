package com.simplifiedbilling.inventory.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String name,
        String receiptName,
        String sku,
        String barcode,
        boolean internalBarcode,
        CategoryResponse category,
        ProductUnit unit,
        String hsnCode,
        BigDecimal gstRate,
        BigDecimal purchaseCost,
        BigDecimal sellingPrice,
        BigDecimal stockQuantity,
        BigDecimal minimumStockLevel,
        StockStatus stockStatus,
        boolean active,
        long version,
        long stockVersion,
        Instant createdAt,
        Instant updatedAt) {
}
