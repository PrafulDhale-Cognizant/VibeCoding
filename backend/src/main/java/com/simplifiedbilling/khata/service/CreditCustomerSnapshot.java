package com.simplifiedbilling.khata.service;

import java.math.BigDecimal;

public record CreditCustomerSnapshot(
        String customerId,
        String name,
        String phone,
        BigDecimal outstandingAmount) {
}
