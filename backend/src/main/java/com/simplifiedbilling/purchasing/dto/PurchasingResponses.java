package com.simplifiedbilling.purchasing.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.purchasing.domain.PurchaseStatus;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntryType;
import com.simplifiedbilling.purchasing.domain.SupplierPaymentMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PurchasingResponses {

    private PurchasingResponses() {
    }

    public record SupplierResponse(
            String id,
            String name,
            String phone,
            String gstin,
            String address,
            String notes,
            boolean active,
            BigDecimal outstandingAmount,
            long version,
            long balanceVersion,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SummaryResponse(
            BigDecimal totalOutstanding,
            long suppliersWithDue,
            long activeSuppliers) {
    }

    public record SupplierLedgerResponse(
            String id,
            String supplierId,
            SupplierLedgerEntryType entryType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String purchaseId,
            String purchaseNumber,
            SupplierPaymentMode paymentMode,
            String paymentReference,
            String notes,
            String actorUserId,
            Instant occurredAt) {
    }

    public record SupplierPaymentResponse(
            String entryId,
            String supplierId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            SupplierPaymentMode paymentMode,
            Instant occurredAt,
            boolean idempotentReplay) {
    }

    public record PurchaseSummaryResponse(
            String id,
            String purchaseNumber,
            String supplierId,
            String supplierName,
            String supplierInvoiceNumber,
            LocalDate invoiceDate,
            PurchaseStatus status,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal outstandingAdded,
            Instant receivedAt) {
    }

    public record PurchaseResponse(
            String id,
            String purchaseNumber,
            String supplierId,
            String supplierName,
            String supplierInvoiceNumber,
            LocalDate invoiceDate,
            PurchaseStatus status,
            boolean pricesIncludeTax,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal outstandingAdded,
            SupplierPaymentMode paymentMode,
            String paymentReference,
            String notes,
            String actorUserId,
            Instant receivedAt,
            List<PurchaseLineResponse> items,
            boolean idempotentReplay) {

        public PurchaseResponse {
            items = List.copyOf(items);
        }
    }

    public record PurchaseLineResponse(
            int lineNumber,
            String productId,
            String productName,
            ProductUnit unit,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal gstRate,
            BigDecimal taxableAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
    }
}
