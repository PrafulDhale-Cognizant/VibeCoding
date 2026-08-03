package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.pos.domain.InvoiceStatus;
import com.simplifiedbilling.pos.domain.PaymentMode;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceSearchCriteria(
        String query,
        InvoiceStatus status,
        PaymentMode paymentMode,
        Instant from,
        Instant to,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String sort,
        int page,
        int size) {
}
