package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.purchasing.domain.PurchaseItem;

import java.math.BigDecimal;

public record PurchaseReturnSelection(
        PurchaseItem purchaseItem,
        BigDecimal quantity) {
}
