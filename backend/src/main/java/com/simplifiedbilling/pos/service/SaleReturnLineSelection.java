package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.pos.domain.InvoiceItem;
import com.simplifiedbilling.pos.domain.ReturnDisposition;

import java.math.BigDecimal;

public record SaleReturnLineSelection(
        InvoiceItem invoiceItem,
        BigDecimal quantity,
        ReturnDisposition disposition,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal lineTotal) {
}
