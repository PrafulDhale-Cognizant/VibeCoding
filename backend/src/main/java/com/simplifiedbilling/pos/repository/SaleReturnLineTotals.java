package com.simplifiedbilling.pos.repository;

import java.math.BigDecimal;

public interface SaleReturnLineTotals {
    BigDecimal getQuantity();
    BigDecimal getGrossAmount();
    BigDecimal getDiscountAmount();
    BigDecimal getTaxableAmount();
    BigDecimal getCgstAmount();
    BigDecimal getSgstAmount();
    BigDecimal getIgstAmount();
    BigDecimal getLineTotal();
}
