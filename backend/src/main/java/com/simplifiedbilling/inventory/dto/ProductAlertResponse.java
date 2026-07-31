package com.simplifiedbilling.inventory.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockStatus;

import java.math.BigDecimal;

public record ProductAlertResponse(
        String productId,
        String name,
        String sku,
        ProductUnit unit,
        BigDecimal stockQuantity,
        BigDecimal minimumStockLevel,
        BigDecimal suggestedReorderQuantity,
        StockStatus stockStatus) {
}
