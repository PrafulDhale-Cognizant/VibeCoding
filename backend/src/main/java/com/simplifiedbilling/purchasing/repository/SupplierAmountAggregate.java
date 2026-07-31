package com.simplifiedbilling.purchasing.repository;

import java.math.BigDecimal;

public interface SupplierAmountAggregate {
    String getSupplierId();
    BigDecimal getAmount();
}
