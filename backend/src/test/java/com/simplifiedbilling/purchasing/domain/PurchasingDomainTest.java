package com.simplifiedbilling.purchasing.domain;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.service.PurchaseProductSnapshot;
import com.simplifiedbilling.purchasing.mapper.PurchasingMapper;
import com.simplifiedbilling.purchasing.service.PurchasePricingEngine;
import com.simplifiedbilling.purchasing.service.PurchaseReturnSelection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchasingDomainTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Test
    void supplierOwnsVersionedPayableAndCanBeUpdated() {
        Supplier supplier = Supplier.create(
                "Fresh Foods", "9876543210", "27ABCDE1234F1Z5", "Market", "Weekly", NOW);

        assertThat(supplier.getId()).isNotBlank();
        assertThat(supplier.isNew()).isTrue();
        assertThat(supplier.getName()).isEqualTo("Fresh Foods");
        assertThat(supplier.getPhone()).isEqualTo("9876543210");
        assertThat(supplier.getGstin()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(supplier.getAddress()).isEqualTo("Market");
        assertThat(supplier.getNotes()).isEqualTo("Weekly");
        assertThat(supplier.isActive()).isTrue();
        assertThat(supplier.getVersion()).isZero();
        assertThat(supplier.getCreatedAt()).isEqualTo(NOW);

        SupplierPayableBalance balance = supplier.getPayableBalance();
        assertThat(balance.getSupplierId()).isEqualTo(supplier.getId());
        assertThat(balance.getSupplier()).isSameAs(supplier);
        assertThat(balance.getOutstandingAmount()).isZero();
        assertThat(balance.getCreditAmount()).isZero();
        assertThat(balance.getVersion()).isZero();
        assertThat(balance.addPayable(new BigDecimal("500.00"), NOW.plusSeconds(1)))
                .isEqualByComparingTo("500.00");
        assertThat(balance.pay(new BigDecimal("125.00"), NOW.plusSeconds(2)))
                .isEqualByComparingTo("375.00");
        assertThat(balance.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThatThrownBy(() -> balance.pay(new BigDecimal("376.00"), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        supplier.update("Fresh Foods Ltd", "9999999999", null, null, null, false, NOW.plusSeconds(3));
        assertThat(supplier.getName()).isEqualTo("Fresh Foods Ltd");
        assertThat(supplier.getPhone()).isEqualTo("9999999999");
        assertThat(supplier.getGstin()).isNull();
        assertThat(supplier.getAddress()).isNull();
        assertThat(supplier.getNotes()).isNull();
        assertThat(supplier.isActive()).isFalse();
        supplier.markNotNew();
        assertThat(supplier.isNew()).isFalse();
    }

    @Test
    void purchaseAndLedgerFactoriesCaptureImmutableSnapshots() {
        Supplier supplier = Supplier.create("Fresh Foods", "9876543210", null, null, null, NOW);
        var product = new PurchaseProductSnapshot(
                "product-1", "Rice", ProductUnit.KILOGRAM, new BigDecimal("2.000"),
                new BigDecimal("118.00"), new BigDecimal("18.00"));
        var pricing = new PurchasePricingEngine().calculate(List.of(product), true);
        Purchase purchase = Purchase.received(
                "purchase-1", "PUR-000001", "purchase-key", supplier, "SUP-7",
                LocalDate.of(2026, 8, 1), pricing, new BigDecimal("100.00"),
                SupplierPaymentMode.UPI, "UPI-7", "Received", "actor", NOW);

        assertThat(purchase.getId()).isEqualTo("purchase-1");
        assertThat(purchase.isNew()).isTrue();
        assertThat(purchase.getPurchaseNumber()).isEqualTo("PUR-000001");
        assertThat(purchase.getIdempotencyKey()).isEqualTo("purchase-key");
        assertThat(purchase.getSupplier()).isSameAs(supplier);
        assertThat(purchase.getSupplierName()).isEqualTo("Fresh Foods");
        assertThat(purchase.getSupplierInvoiceNumber()).isEqualTo("SUP-7");
        assertThat(purchase.getInvoiceDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.RECEIVED);
        assertThat(purchase.isPricesIncludeTax()).isTrue();
        assertThat(purchase.getSubtotalAmount()).isEqualByComparingTo("200.00");
        assertThat(purchase.getTaxAmount()).isEqualByComparingTo("36.00");
        assertThat(purchase.getTotalAmount()).isEqualByComparingTo("236.00");
        assertThat(purchase.getAmountPaid()).isEqualByComparingTo("100.00");
        assertThat(purchase.getOutstandingAdded()).isEqualByComparingTo("136.00");
        assertThat(purchase.getPaymentMode()).isEqualTo(SupplierPaymentMode.UPI);
        assertThat(purchase.getPaymentReference()).isEqualTo("UPI-7");
        assertThat(purchase.getNotes()).isEqualTo("Received");
        assertThat(purchase.getActorUserId()).isEqualTo("actor");
        assertThat(purchase.getReceivedAt()).isEqualTo(NOW);
        assertThat(purchase.getCreatedAt()).isEqualTo(NOW);

        PurchaseItem item = purchase.getItems().getFirst();
        assertThat(item.getLineNumber()).isEqualTo(1);
        assertThat(item.getProductId()).isEqualTo("product-1");
        assertThat(item.getProductName()).isEqualTo("Rice");
        assertThat(item.getUnit()).isEqualTo(ProductUnit.KILOGRAM);
        assertThat(item.getQuantity()).isEqualByComparingTo("2.000");
        assertThat(item.getReturnedQuantity()).isZero();
        assertThat(item.getReturnableQuantity()).isEqualByComparingTo("2.000");
        assertThat(item.getUnitCost()).isEqualByComparingTo("118.00");
        assertThat(item.getGstRate()).isEqualByComparingTo("18.00");
        assertThat(item.getTaxableAmount()).isEqualByComparingTo("200.00");
        assertThat(item.getTaxAmount()).isEqualByComparingTo("36.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("236.00");
        purchase.markNotNew();
        assertThat(purchase.isNew()).isFalse();

        SupplierLedgerEntry due = SupplierLedgerEntry.purchaseDue(
                supplier, purchase, new BigDecimal("136.00"), new BigDecimal("136.00"), "actor", NOW);
        assertThat(due.getEntryType()).isEqualTo(SupplierLedgerEntryType.PURCHASE_DUE);
        assertThat(due.getPurchase()).isSameAs(purchase);
        assertThat(due.getIdempotencyKey()).isNull();
        assertThat(due.getPaymentMode()).isNull();
        assertThat(due.getNotes()).isEqualTo("Purchase received on credit");

        SupplierLedgerEntry payment = SupplierLedgerEntry.payment(
                supplier, new BigDecimal("50.00"), new BigDecimal("86.00"),
                SupplierPaymentMode.BANK_TRANSFER, "payment-key", "NEFT-1", "Partial", "actor", NOW);
        assertThat(payment.getId()).isNotBlank();
        assertThat(payment.isNew()).isTrue();
        assertThat(payment.getSupplier()).isSameAs(supplier);
        assertThat(payment.getEntryType()).isEqualTo(SupplierLedgerEntryType.PAYMENT);
        assertThat(payment.getAmount()).isEqualByComparingTo("50.00");
        assertThat(payment.getBalanceAfter()).isEqualByComparingTo("86.00");
        assertThat(payment.getPurchase()).isNull();
        assertThat(payment.getIdempotencyKey()).isEqualTo("payment-key");
        assertThat(payment.getPaymentMode()).isEqualTo(SupplierPaymentMode.BANK_TRANSFER);
        assertThat(payment.getPaymentReference()).isEqualTo("NEFT-1");
        assertThat(payment.getNotes()).isEqualTo("Partial");
        assertThat(payment.getActorUserId()).isEqualTo("actor");
        assertThat(payment.getOccurredAt()).isEqualTo(NOW);
        payment.markNotNew();
        assertThat(payment.isNew()).isFalse();
    }

    @Test
    void mapperCreatesSupplierPurchaseLedgerAndPaymentDtos() {
        Supplier supplier = Supplier.create("Fresh Foods", "9876543210", null, null, null, NOW);
        var pricing = new PurchasePricingEngine().calculate(List.of(new PurchaseProductSnapshot(
                "product", "Rice", ProductUnit.PIECE, BigDecimal.ONE,
                new BigDecimal("100.00"), new BigDecimal("5.00"))), false);
        Purchase purchase = Purchase.received(
                "purchase", "PUR-1", "purchase-key", supplier, null, LocalDate.now(), pricing,
                ZERO, null, null, null, "actor", NOW);
        supplier.getPayableBalance().addPayable(purchase.getOutstandingAdded(), NOW);
        SupplierLedgerEntry entry = SupplierLedgerEntry.purchaseDue(
                supplier, purchase, purchase.getOutstandingAdded(), purchase.getOutstandingAdded(), "actor", NOW);
        PurchasingMapper mapper = new PurchasingMapper();

        assertThat(mapper.toSupplier(supplier).outstandingAmount()).isEqualByComparingTo("105.00");
        assertThat(mapper.toPurchaseSummary(purchase).purchaseNumber()).isEqualTo("PUR-1");
        assertThat(mapper.toPurchase(purchase, true).items()).hasSize(1);
        assertThat(mapper.toPurchase(purchase, true).idempotentReplay()).isTrue();
        assertThat(mapper.toLedger(entry).purchaseNumber()).isEqualTo("PUR-1");

        SupplierLedgerEntry payment = SupplierLedgerEntry.payment(
                supplier, new BigDecimal("5.00"), new BigDecimal("100.00"),
                SupplierPaymentMode.CASH, "payment-key", null, null, "actor", NOW);
        assertThat(mapper.toLedger(payment).purchaseId()).isNull();
        assertThat(mapper.toPayment(payment, true).idempotentReplay()).isTrue();
    }

    @Test
    void returnFactoriesTrackCumulativeQuantityAndTwoSidedSupplierBalance() {
        Supplier supplier = Supplier.create("Fresh Foods", "9876543210", null, null, null, NOW);
        supplier.getPayableBalance().addPayable(new BigDecimal("30.00"), NOW);
        var purchasePricing = new PurchasePricingEngine().calculate(List.of(new PurchaseProductSnapshot(
                "product", "Rice", ProductUnit.KILOGRAM, new BigDecimal("2.000"),
                new BigDecimal("118.00"), new BigDecimal("18.00"))), true);
        Purchase purchase = Purchase.received(
                "purchase", "PUR-1", "purchase-key", supplier, null,
                LocalDate.of(2026, 8, 1), purchasePricing, ZERO,
                null, null, null, "actor", NOW);
        PurchaseItem source = purchase.getItems().getFirst();
        PurchaseReturnSelection selection = new PurchaseReturnSelection(
                source, new BigDecimal("0.500"));
        var returnPricing = new PurchasePricingEngine().calculate(List.of(new PurchaseProductSnapshot(
                source.getProductId(), source.getProductName(), source.getUnit(), selection.quantity(),
                source.getUnitCost(), source.getGstRate())), true);
        var movement = supplier.getPayableBalance().applyReturn(
                returnPricing.totalAmount(), NOW.plusSeconds(1));
        source.registerReturn(selection.quantity());
        PurchaseReturn purchaseReturn = PurchaseReturn.completed(
                "return", "PRN-1", "return-key", purchase,
                LocalDate.of(2026, 8, 2), PurchaseReturnReason.QUALITY_ISSUE,
                returnPricing, List.of(selection), movement, "Accepted", "actor", NOW.plusSeconds(1));

        assertThat(source.getReturnedQuantity()).isEqualByComparingTo("0.500");
        assertThat(source.getReturnableQuantity()).isEqualByComparingTo("1.500");
        assertThatThrownBy(() -> source.registerReturn(new BigDecimal("1.501")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.registerReturn(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(purchaseReturn.getReturnNumber()).isEqualTo("PRN-1");
        assertThat(purchaseReturn.getPurchase()).isSameAs(purchase);
        assertThat(purchaseReturn.getReason()).isEqualTo(PurchaseReturnReason.QUALITY_ISSUE);
        assertThat(purchaseReturn.getTotalAmount()).isEqualByComparingTo("59.00");
        assertThat(purchaseReturn.getPayableReduction()).isEqualByComparingTo("30.00");
        assertThat(purchaseReturn.getCreditAdded()).isEqualByComparingTo("29.00");
        assertThat(purchaseReturn.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getPurchaseItem()).isSameAs(source);
            assertThat(item.getQuantity()).isEqualByComparingTo("0.500");
        });

        SupplierLedgerEntry ledger = SupplierLedgerEntry.purchaseReturn(
                supplier, purchaseReturn, purchaseReturn.getTotalAmount(),
                movement.payableAfter(), movement.creditAfter(), "actor", NOW.plusSeconds(1));
        assertThat(ledger.getEntryType()).isEqualTo(SupplierLedgerEntryType.PURCHASE_RETURN);
        assertThat(ledger.getPurchaseReturn()).isSameAs(purchaseReturn);
        assertThat(new PurchasingMapper().toPurchaseReturn(purchaseReturn, false).items())
                .singleElement().satisfies(item -> assertThat(item.purchaseItemId()).isEqualTo(source.getId()));
        assertThat(new PurchasingMapper().toLedger(ledger).purchaseReturnNumber()).isEqualTo("PRN-1");

        var firstPurchase = supplier.getPayableBalance().applyPurchase(
                new BigDecimal("20.00"), NOW.plusSeconds(2));
        assertThat(firstPurchase.payableAfter()).isZero();
        assertThat(firstPurchase.creditAfter()).isEqualByComparingTo("9.00");
        var secondPurchase = supplier.getPayableBalance().applyPurchase(
                new BigDecimal("19.00"), NOW.plusSeconds(3));
        assertThat(secondPurchase.payableAfter()).isEqualByComparingTo("10.00");
        assertThat(secondPurchase.creditAfter()).isZero();
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");
}
