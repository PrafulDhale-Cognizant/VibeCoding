package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.domain.StockStatus;

public record ProductSearch(
        String query,
        String categoryId,
        Boolean active,
        StockStatus stockStatus,
        int page,
        int size,
        ProductSort sort) {
}
