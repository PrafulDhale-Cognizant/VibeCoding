package com.simplifiedbilling.inventory.dto;

import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record StockTransactionResponse(
        String id,
        String productId,
        StockTransactionType transactionType,
        BigDecimal quantityDelta,
        BigDecimal balanceAfter,
        StockReasonCode reasonCode,
        String referenceType,
        String referenceId,
        String notes,
        String actorUserId,
        Instant occurredAt) {
}
