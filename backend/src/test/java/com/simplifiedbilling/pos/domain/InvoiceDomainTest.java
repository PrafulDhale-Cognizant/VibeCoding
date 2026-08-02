package com.simplifiedbilling.pos.domain;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.service.SaleProductSnapshot;
import com.simplifiedbilling.pos.mapper.PosMapper;
import com.simplifiedbilling.store.domain.ReceiptWidth;
import com.simplifiedbilling.store.dto.StoreDetails;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceDomainTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void completedInvoiceSnapshotsPricingProductsAndPayments() {
        PricingResult pricing = pricing();
        Invoice invoice = Invoice.completed(
                "invoice-id",
                "INV-000001",
                "checkout-key",
                "cashier-id",
                pricing,
                List.of(
                        new PaymentAllocation(
                                PaymentMode.CASH,
                                new BigDecimal("70.00"),
                                new BigDecimal("100.00"),
                                new BigDecimal("30.00"),
                                null, null, null),
                        new PaymentAllocation(
                                PaymentMode.UDHAAR,
                                new BigDecimal("30.00"),
                                null,
                                BigDecimal.ZERO.setScale(2),
                                null, "customer-1", "Ravi")),
                "Counter sale",
                NOW);

        assertThat(invoice.getId()).isEqualTo("invoice-id");
        assertThat(invoice.isNew()).isTrue();
        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-000001");
        assertThat(invoice.getIdempotencyKey()).isEqualTo("checkout-key");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.COMPLETED);
        assertThat(invoice.getCashierUserId()).isEqualTo("cashier-id");
        assertThat(invoice.getTaxMode()).isEqualTo(TaxMode.INTRA_STATE);
        assertThat(invoice.isPricesIncludeGst()).isTrue();
        assertThat(invoice.isGstApplied()).isTrue();
        assertThat(invoice.getSubtotalAmount()).isEqualByComparingTo("100.00");
        assertThat(invoice.getLineDiscountAmount()).isZero();
        assertThat(invoice.getBillDiscountAmount()).isZero();
        assertThat(invoice.getTaxableAmount()).isEqualByComparingTo("95.24");
        assertThat(invoice.getCgstAmount()).isEqualByComparingTo("2.38");
        assertThat(invoice.getSgstAmount()).isEqualByComparingTo("2.38");
        assertThat(invoice.getIgstAmount()).isZero();
        assertThat(invoice.getRoundOffAmount()).isZero();
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(invoice.getNotes()).isEqualTo("Counter sale");
        assertThat(invoice.getCompletedAt()).isEqualTo(NOW);
        assertThat(invoice.getCreatedAt()).isEqualTo(NOW);

        InvoiceItem item = invoice.getItems().getFirst();
        assertThat(item.getLineNumber()).isEqualTo(1);
        assertThat(item.getProductId()).isEqualTo("product-id");
        assertThat(item.getProductName()).isEqualTo("Rice bag");
        assertThat(item.getReceiptName()).isEqualTo("Rice");
        assertThat(item.getBarcode()).isEqualTo("1234");
        assertThat(item.getUnit()).isEqualTo(ProductUnit.PIECE);
        assertThat(item.getQuantity()).isEqualByComparingTo("1.000");
        assertThat(item.getPurchaseCost()).isEqualByComparingTo("80.00");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(item.getGstRate()).isEqualByComparingTo("5.00");
        assertThat(item.getGrossAmount()).isEqualByComparingTo("100.00");
        assertThat(item.getLineDiscountAmount()).isZero();
        assertThat(item.getBillDiscountAmount()).isZero();
        assertThat(item.getTaxableAmount()).isEqualByComparingTo("95.24");
        assertThat(item.getCgstAmount()).isEqualByComparingTo("2.38");
        assertThat(item.getSgstAmount()).isEqualByComparingTo("2.38");
        assertThat(item.getIgstAmount()).isZero();
        assertThat(item.getLineTotal()).isEqualByComparingTo("100.00");

        Payment cash = invoice.getPayments().getFirst();
        assertThat(cash.getMode()).isEqualTo(PaymentMode.CASH);
        assertThat(cash.getAmount()).isEqualByComparingTo("70.00");
        assertThat(cash.getTenderedAmount()).isEqualByComparingTo("100.00");
        assertThat(cash.getChangeAmount()).isEqualByComparingTo("30.00");
        assertThat(cash.getReference()).isNull();
        assertThat(cash.getRecordedAt()).isEqualTo(NOW);
        assertThat(invoice.getPayments().get(1).getCustomerId()).isEqualTo("customer-1");
        assertThat(invoice.getPayments().get(1).getCustomerName()).isEqualTo("Ravi");
        invoice.markNotNew();
        assertThat(invoice.isNew()).isFalse();
    }

    @Test
    void mapperBuildsLiveQuoteAndImmutableReceipt() {
        PosMapper mapper = new PosMapper();
        PricingResult pricing = pricing();

        var quote = mapper.toQuote(pricing);

        assertThat(quote.lines()).hasSize(1);
        assertThat(quote.lines().getFirst().availableQuantity()).isEqualByComparingTo("9.000");
        assertThat(quote.lines().getFirst().name()).isEqualTo("Rice bag");
        assertThat(quote.gstApplied()).isTrue();
        assertThat(quote.totalAmount()).isEqualByComparingTo("100.00");

        Invoice invoice = Invoice.completed(
                "invoice-id", "INV-1", "checkout-key", "cashier", pricing,
                List.of(new PaymentAllocation(
                        PaymentMode.CARD, new BigDecimal("100.00"), null,
                        BigDecimal.ZERO.setScale(2), "CARD-1", null, null)), null, NOW);
        StoreDetails store = new StoreDetails(
                "My Shop", "Owner", "Line 1", " Line 2 ", "Pune", "Maharashtra", "27",
                "411001", "9999999999", null, false, null, "INR", "Asia/Kolkata", "INV",
                4, ReceiptWidth.MM_58, false, 0, NOW, NOW);

        var response = mapper.toInvoice(invoice, store, true);

        assertThat(response.store().address()).isEqualTo("Line 1, Line 2, Pune, Maharashtra 411001");
        assertThat(response.store().receiptWidth()).isEqualTo(ReceiptWidth.MM_58);
        assertThat(response.totals().lines().getFirst().availableQuantity()).isNull();
        assertThat(response.totals().gstApplied()).isTrue();
        assertThat(response.payments().getFirst().mode()).isEqualTo(PaymentMode.CARD);
        assertThat(response.payments().getFirst().reference()).isEqualTo("CARD-1");
        assertThat(response.idempotentReplay()).isTrue();
    }

    private PricingResult pricing() {
        SaleProductSnapshot product = new SaleProductSnapshot(
                "product-id", "Rice bag", "Rice", "1234", ProductUnit.PIECE,
                new BigDecimal("5.00"), new BigDecimal("80.00"), new BigDecimal("100.00"),
                new BigDecimal("9.000"));
        PricingLine line = new PricingLine(
                1, product, new BigDecimal("1.000"), new BigDecimal("100.00"),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                new BigDecimal("95.24"), new BigDecimal("2.38"), new BigDecimal("2.38"),
                BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"));
        return new PricingResult(
                List.of(line), TaxMode.INTRA_STATE, true, true, new BigDecimal("100.00"),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), new BigDecimal("95.24"),
                new BigDecimal("2.38"), new BigDecimal("2.38"), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"));
    }
}
