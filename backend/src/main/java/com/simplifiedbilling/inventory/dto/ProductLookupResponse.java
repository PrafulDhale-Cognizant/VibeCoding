package com.simplifiedbilling.inventory.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;

import java.math.BigDecimal;

public record ProductLookupResponse(
        String id,
        String name,
        String receiptName,
        String barcode,
        ProductUnit unit,
        BigDecimal gstRate,
        BigDecimal sellingPrice,
        BigDecimal stockQuantity,
        boolean active) {
}
