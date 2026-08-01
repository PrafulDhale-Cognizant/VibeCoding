package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.pos.domain.PaymentMode;

import java.math.BigDecimal;

public record RefundAllocation(
        PaymentMode mode,
        BigDecimal amount,
        String reference,
        String customerId) {
}
