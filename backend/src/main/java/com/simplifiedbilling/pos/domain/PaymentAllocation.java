package com.simplifiedbilling.pos.domain;

import java.math.BigDecimal;

public record PaymentAllocation(
        PaymentMode mode,
        BigDecimal amount,
        BigDecimal tenderedAmount,
        BigDecimal changeAmount,
        String reference,
        String customerId,
        String customerName,
        String customerPhone) {

    public PaymentAllocation(
            PaymentMode mode, BigDecimal amount, BigDecimal tenderedAmount, BigDecimal changeAmount,
            String reference, String customerId, String customerName) {
        this(mode, amount, tenderedAmount, changeAmount, reference, customerId, customerName, null);
    }
}
