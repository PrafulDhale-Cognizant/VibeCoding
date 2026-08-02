package com.simplifiedbilling.pos.mapper;

import com.simplifiedbilling.pos.domain.Invoice;
import com.simplifiedbilling.pos.domain.InvoiceItem;
import com.simplifiedbilling.pos.domain.PricingLine;
import com.simplifiedbilling.pos.domain.PricingResult;
import com.simplifiedbilling.pos.dto.PosResponses;
import com.simplifiedbilling.store.dto.StoreDetails;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;

@Component
public class PosMapper {

    public PosResponses.QuoteResponse toQuote(PricingResult pricing) {
        return new PosResponses.QuoteResponse(
                pricing.lines().stream().map(this::toLine).toList(),
                pricing.taxMode(),
                pricing.pricesIncludeGst(),
                pricing.customerGstin(),
                pricing.subtotalAmount(),
                pricing.lineDiscountAmount(),
                pricing.billDiscountAmount(),
                pricing.taxableAmount(),
                pricing.cgstAmount(),
                pricing.sgstAmount(),
                pricing.igstAmount(),
                pricing.roundOffAmount(),
                pricing.totalAmount());
    }

    public PosResponses.InvoiceResponse toInvoice(
            Invoice invoice,
            StoreDetails store,
            boolean idempotentReplay) {
        PosResponses.QuoteResponse totals = new PosResponses.QuoteResponse(
                invoice.getItems().stream()
                        .sorted(Comparator.comparingInt(InvoiceItem::getLineNumber))
                        .map(this::toLine)
                        .toList(),
                invoice.getTaxMode(),
                invoice.isPricesIncludeGst(),
                invoice.getCustomerGstin(),
                invoice.getSubtotalAmount(),
                invoice.getLineDiscountAmount(),
                invoice.getBillDiscountAmount(),
                invoice.getTaxableAmount(),
                invoice.getCgstAmount(),
                invoice.getSgstAmount(),
                invoice.getIgstAmount(),
                invoice.getRoundOffAmount(),
                invoice.getTotalAmount());
        String address = joinAddress(store);
        PosResponses.StoreReceiptResponse receiptStore = new PosResponses.StoreReceiptResponse(
                store.shopName(), address, store.phone(), store.gstin(), store.receiptWidth());
        return new PosResponses.InvoiceResponse(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getStatus(),
                invoice.getCashierUserId(), invoice.getCompletedAt(), invoice.getNotes(),
                receiptStore, totals,
                invoice.getPayments().stream().map(payment -> new PosResponses.PaymentResponse(
                        payment.getMode(), payment.getAmount(), payment.getTenderedAmount(),
                        payment.getChangeAmount(), payment.getReference(), payment.getCustomerId(),
                        payment.getCustomerName(), payment.getCustomerPhone())).toList(),
                idempotentReplay);
    }

    private PosResponses.QuoteLineResponse toLine(PricingLine line) {
        return new PosResponses.QuoteLineResponse(
                line.lineNumber(), line.product().productId(), line.product().name(),
                line.product().receiptName(), line.product().barcode(), line.product().unit(),
                line.quantity(), line.product().availableQuantity(), line.product().sellingPrice(),
                line.product().gstRate(), line.grossAmount(), line.lineDiscountAmount(),
                line.billDiscountAmount(), line.taxableAmount(), line.cgstAmount(),
                line.sgstAmount(), line.igstAmount(), line.lineTotal());
    }

    private PosResponses.QuoteLineResponse toLine(InvoiceItem line) {
        return new PosResponses.QuoteLineResponse(
                line.getLineNumber(), line.getProductId(), line.getProductName(), line.getReceiptName(),
                line.getBarcode(), line.getUnit(), line.getQuantity(), null, line.getUnitPrice(),
                line.getGstRate(), line.getGrossAmount(), line.getLineDiscountAmount(),
                line.getBillDiscountAmount(), line.getTaxableAmount(), line.getCgstAmount(),
                line.getSgstAmount(), line.getIgstAmount(), line.getLineTotal());
    }

    private String joinAddress(StoreDetails store) {
        StringBuilder address = new StringBuilder(store.addressLine1());
        append(address, store.addressLine2());
        append(address, store.city());
        append(address, store.stateName() + " " + store.postalCode());
        return address.toString();
    }

    private void append(StringBuilder target, String value) {
        if (value != null && !value.isBlank()) {
            target.append(", ").append(value.trim());
        }
    }
}
