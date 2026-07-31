package com.simplifiedbilling.purchasing.mapper;

import com.simplifiedbilling.purchasing.domain.Purchase;
import com.simplifiedbilling.purchasing.domain.PurchaseItem;
import com.simplifiedbilling.purchasing.domain.Supplier;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntry;
import com.simplifiedbilling.purchasing.dto.PurchasingResponses;
import org.springframework.stereotype.Component;

@Component
public class PurchasingMapper {

    public PurchasingResponses.SupplierResponse toSupplier(Supplier supplier) {
        return new PurchasingResponses.SupplierResponse(
                supplier.getId(), supplier.getName(), supplier.getPhone(), supplier.getGstin(),
                supplier.getAddress(), supplier.getNotes(), supplier.isActive(),
                supplier.getPayableBalance().getOutstandingAmount(), supplier.getVersion(),
                supplier.getPayableBalance().getVersion(), supplier.getCreatedAt(), supplier.getUpdatedAt());
    }

    public PurchasingResponses.PurchaseSummaryResponse toPurchaseSummary(Purchase purchase) {
        return new PurchasingResponses.PurchaseSummaryResponse(
                purchase.getId(), purchase.getPurchaseNumber(), purchase.getSupplier().getId(),
                purchase.getSupplierName(), purchase.getSupplierInvoiceNumber(), purchase.getInvoiceDate(),
                purchase.getStatus(), purchase.getTotalAmount(), purchase.getAmountPaid(),
                purchase.getOutstandingAdded(), purchase.getReceivedAt());
    }

    public PurchasingResponses.PurchaseResponse toPurchase(Purchase purchase, boolean replay) {
        return new PurchasingResponses.PurchaseResponse(
                purchase.getId(), purchase.getPurchaseNumber(), purchase.getSupplier().getId(),
                purchase.getSupplierName(), purchase.getSupplierInvoiceNumber(), purchase.getInvoiceDate(),
                purchase.getStatus(), purchase.isPricesIncludeTax(), purchase.getSubtotalAmount(),
                purchase.getTaxAmount(), purchase.getTotalAmount(), purchase.getAmountPaid(),
                purchase.getOutstandingAdded(), purchase.getPaymentMode(), purchase.getPaymentReference(),
                purchase.getNotes(), purchase.getActorUserId(), purchase.getReceivedAt(),
                purchase.getItems().stream().map(this::toPurchaseLine).toList(), replay);
    }

    public PurchasingResponses.SupplierLedgerResponse toLedger(SupplierLedgerEntry entry) {
        Purchase purchase = entry.getPurchase();
        return new PurchasingResponses.SupplierLedgerResponse(
                entry.getId(), entry.getSupplier().getId(), entry.getEntryType(), entry.getAmount(),
                entry.getBalanceAfter(), purchase == null ? null : purchase.getId(),
                purchase == null ? null : purchase.getPurchaseNumber(), entry.getPaymentMode(),
                entry.getPaymentReference(), entry.getNotes(), entry.getActorUserId(), entry.getOccurredAt());
    }

    public PurchasingResponses.SupplierPaymentResponse toPayment(
            SupplierLedgerEntry entry, boolean replay) {
        return new PurchasingResponses.SupplierPaymentResponse(
                entry.getId(), entry.getSupplier().getId(), entry.getAmount(), entry.getBalanceAfter(),
                entry.getPaymentMode(), entry.getOccurredAt(), replay);
    }

    private PurchasingResponses.PurchaseLineResponse toPurchaseLine(PurchaseItem item) {
        return new PurchasingResponses.PurchaseLineResponse(
                item.getLineNumber(), item.getProductId(), item.getProductName(), item.getUnit(),
                item.getQuantity(), item.getUnitCost(), item.getGstRate(), item.getTaxableAmount(),
                item.getTaxAmount(), item.getLineTotal());
    }
}
