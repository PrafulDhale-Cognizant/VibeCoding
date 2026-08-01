package com.simplifiedbilling.pos.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.pos.domain.InvoiceStatus;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.domain.ReturnDisposition;
import com.simplifiedbilling.pos.domain.SaleReturnType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SaleReturnResponses {
    private SaleReturnResponses() { }

    public record InvoiceLine(
            String invoiceItemId, int lineNumber, String productId, String productName,
            ProductUnit unit, BigDecimal soldQuantity, BigDecimal returnedQuantity,
            BigDecimal returnableQuantity, BigDecimal unitPrice, BigDecimal lineTotal,
            BigDecimal returnedAmount, BigDecimal returnableAmount) { }

    public record SourceInvoice(
            String id, String invoiceNumber, InvoiceStatus status, Instant completedAt,
            BigDecimal totalAmount, BigDecimal returnableTotal,
            List<PosResponses.PaymentResponse> payments,
            List<InvoiceLine> items) { }

    public record ReturnLine(
            String invoiceItemId, int lineNumber, String productId, String productName,
            ProductUnit unit, BigDecimal quantity, ReturnDisposition disposition,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal taxableAmount,
            BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount,
            BigDecimal lineTotal) { }

    public record Refund(PaymentMode mode, BigDecimal amount, String reference, String customerId) { }

    public record ReturnResponse(
            String id, String returnNumber, String invoiceId, String invoiceNumber,
            SaleReturnType type, String reason, BigDecimal subtotalAmount,
            BigDecimal discountAmount, BigDecimal taxableAmount, BigDecimal cgstAmount,
            BigDecimal sgstAmount, BigDecimal igstAmount, BigDecimal totalAmount,
            Instant returnedAt, List<ReturnLine> items, List<Refund> refunds,
            boolean idempotentReplay) { }
}
