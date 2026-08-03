package com.simplifiedbilling.pos.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.pos.domain.InvoiceStatus;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.domain.TaxMode;
import com.simplifiedbilling.store.domain.ReceiptWidth;
import com.simplifiedbilling.store.domain.A4InvoiceTemplate;
import com.simplifiedbilling.store.domain.ThermalReceiptTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PosResponses {

    private PosResponses() {
    }

    public record QuoteLineResponse(
            int lineNumber,
            String productId,
            String name,
            String receiptName,
            String barcode,
            ProductUnit unit,
            BigDecimal quantity,
            BigDecimal availableQuantity,
            BigDecimal unitPrice,
            BigDecimal gstRate,
            BigDecimal grossAmount,
            BigDecimal lineDiscountAmount,
            BigDecimal billDiscountAmount,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal lineTotal) {
    }

    public record QuoteResponse(
            List<QuoteLineResponse> lines,
            TaxMode taxMode,
            boolean pricesIncludeGst,
            boolean gstApplied,
            BigDecimal subtotalAmount,
            BigDecimal lineDiscountAmount,
            BigDecimal billDiscountAmount,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal roundOffAmount,
            BigDecimal totalAmount) {
    }

    public record StoreReceiptResponse(
            String shopName,
            String address,
            String phone,
            String gstin,
            ReceiptWidth receiptWidth,
            A4InvoiceTemplate a4InvoiceTemplate,
            ThermalReceiptTemplate thermalReceiptTemplate) {
    }

    public record PaymentResponse(
            PaymentMode mode,
            BigDecimal amount,
            BigDecimal tenderedAmount,
            BigDecimal changeAmount,
            String reference,
            String customerId,
            String customerName,
            String customerPhone) {
    }

    public record InvoiceResponse(
            String id,
            String invoiceNumber,
            InvoiceStatus status,
            String cashierUserId,
            Instant completedAt,
            String notes,
            StoreReceiptResponse store,
            QuoteResponse totals,
            List<PaymentResponse> payments,
            boolean idempotentReplay) {
    }
}
