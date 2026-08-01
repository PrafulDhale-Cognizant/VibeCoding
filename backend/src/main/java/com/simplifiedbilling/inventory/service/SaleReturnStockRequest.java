package com.simplifiedbilling.inventory.service;

import java.math.BigDecimal;

public record SaleReturnStockRequest(String productId, BigDecimal quantity) { }
