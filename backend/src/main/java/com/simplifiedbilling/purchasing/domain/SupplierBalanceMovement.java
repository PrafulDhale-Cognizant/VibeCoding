package com.simplifiedbilling.purchasing.domain;

import java.math.BigDecimal;

public record SupplierBalanceMovement(
        BigDecimal payableMovement,
        BigDecimal creditAdded,
        BigDecimal payableAfter,
        BigDecimal creditAfter) {
}
