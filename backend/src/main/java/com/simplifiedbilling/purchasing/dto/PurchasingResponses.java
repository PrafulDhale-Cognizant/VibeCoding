package com.simplifiedbilling.purchasing.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.purchasing.domain.PurchaseStatus;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntryType;
import com.simplifiedbilling.purchasing.domain.SupplierPaymentMode;
import com.simplifiedbilling.purchasing.domain.PurchaseReturnReason;

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
            BigDecimal creditAmount,
            long version,
            long balanceVersion,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SummaryResponse(
            BigDecimal totalOutstanding,
            BigDecimal totalCredit,
            long suppliersWithDue,
            long suppliersWithCredit,
            long activeSuppliers) {
    }

    public record SupplierLedgerResponse(
            String id,
            String supplierId,
            SupplierLedgerEntryType entryType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            BigDecimal creditBalanceAfter,
            String purchaseId,
            String purchaseNumber,
            String purchaseReturnId,
            String purchaseReturnNumber,
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
            BigDecimal creditBalanceAfter,
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
            String purchaseItemId,
            int lineNumber,
            String productId,
            String productName,
            ProductUnit unit,
            BigDecimal quantity,
            BigDecimal returnedQuantity,
            BigDecimal returnableQuantity,
            BigDecimal unitCost,
            BigDecimal gstRate,
            BigDecimal taxableAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
    }

    public record PurchaseReturnSummaryResponse(
            String id,
            String returnNumber,
            String purchaseId,
            String purchaseNumber,
            String supplierId,
            String supplierName,
            LocalDate returnDate,
            PurchaseReturnReason reason,
            BigDecimal totalAmount,
            BigDecimal payableReduction,
            BigDecimal creditAdded,
            Instant returnedAt) {
    }

    public record PurchaseReturnResponse(
            String id,
            String returnNumber,
            String purchaseId,
            String purchaseNumber,
            String supplierId,
            String supplierName,
            LocalDate returnDate,
            PurchaseReturnReason reason,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal payableReduction,
            BigDecimal creditAdded,
            BigDecimal supplierPayableAfter,
            BigDecimal supplierCreditAfter,
            String notes,
            String actorUserId,
            Instant returnedAt,
            List<PurchaseReturnLineResponse> items,
            boolean idempotentReplay) {

        public PurchaseReturnResponse {
            items = List.copyOf(items);
        }
    }

    public record PurchaseReturnLineResponse(
            int lineNumber,
            String purchaseItemId,
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

    public record SupplierAnalyticsResponse(
            LocalDate from,
            LocalDate to,
            String timezone,
            BigDecimal purchaseTotal,
            BigDecimal returnTotal,
            BigDecimal netPurchaseTotal,
            BigDecimal paymentTotal,
            BigDecimal totalOutstanding,
            BigDecimal totalCredit,
            List<SupplierAnalyticsRowResponse> suppliers,
            Instant generatedAt) {

        public SupplierAnalyticsResponse {
            suppliers = List.copyOf(suppliers);
        }
    }

    public record SupplierAnalyticsRowResponse(
            String supplierId,
            String supplierName,
            BigDecimal purchaseTotal,
            BigDecimal returnTotal,
            BigDecimal netPurchaseTotal,
            BigDecimal paymentTotal,
            BigDecimal outstandingAmount,
            BigDecimal creditAmount) {
    }
}
