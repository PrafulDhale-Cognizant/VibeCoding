package com.simplifiedbilling.inventory.service;

import java.math.BigDecimal;

public record SaleStockRequest(String productId, BigDecimal quantity) {
}
