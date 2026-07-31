package com.simplifiedbilling.pos.domain;

import java.math.BigDecimal;

public record PaymentAllocation(
        PaymentMode mode,
        BigDecimal amount,
        BigDecimal tenderedAmount,
        BigDecimal changeAmount,
        String reference,
        String customerId,
        String customerName) {
}
