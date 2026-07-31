package com.simplifiedbilling.purchasing;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.dto.CategoryCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.service.CategoryService;
import com.simplifiedbilling.inventory.service.ProductService;
import com.simplifiedbilling.inventory.service.StockService;
import com.simplifiedbilling.purchasing.domain.SupplierBalanceStatus;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntryType;
import com.simplifiedbilling.purchasing.domain.SupplierPaymentMode;
import com.simplifiedbilling.purchasing.dto.PurchasingRequests;
import com.simplifiedbilling.purchasing.service.PurchasingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class PurchasingFlowTest {

    @Autowired private CategoryService categoryService;
    @Autowired private ProductService productService;
    @Autowired private StockService stockService;
    @Autowired private PurchasingService purchasingService;

    @Test
    void persistsPurchaseInventoryPayableStatementAndIdempotentPayment() {
        String actor = "purchasing-test-actor";
        var category = categoryService.createCategory(
                actor, new CategoryCreateRequest("Purchase Test"));
        var product = productService.createProduct(
                actor,
                new ProductCreateRequest(
                        "Toor Dal",
                        "Toor Dal",
                        "DAL-TOOR-1",
                        "8901234567005",
                        false,
                        category.id(),
                        ProductUnit.KILOGRAM,
                        "0713",
                        new BigDecimal("5.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("30.00"),
                        new BigDecimal("3.000"),
                        new BigDecimal("2.000")));
        var supplier = purchasingService.createSupplier(
                actor,
                new PurchasingRequests.CreateSupplierRequest(
                        "Fresh Wholesale", "+91 98765 40001", null,
                        "Local market", "Weekly supplier"));

        var received = purchasingService.receivePurchase(
                actor,
                "purchase-flow-1",
                new PurchasingRequests.ReceivePurchaseRequest(
                        supplier.id(),
                        "FW-2026-001",
                        LocalDate.of(2026, 7, 31),
                        true,
                        List.of(new PurchasingRequests.PurchaseItemRequest(
                                product.id(), new BigDecimal("4.500"), new BigDecimal("20.00"))),
                        new BigDecimal("30.00"),
                        SupplierPaymentMode.CASH,
                        null,
                        "Test receipt"));

        assertThat(received.totalAmount()).isEqualByComparingTo("90.00");
        assertThat(received.taxAmount()).isEqualByComparingTo("4.29");
        assertThat(received.outstandingAdded()).isEqualByComparingTo("60.00");
        assertThat(received.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Toor Dal");
            assertThat(item.quantity()).isEqualByComparingTo("4.500");
        });

        var updatedProduct = productService.getProduct(product.id());
        assertThat(updatedProduct.stockQuantity()).isEqualByComparingTo("7.500");
        assertThat(updatedProduct.purchaseCost()).isEqualByComparingTo("20.00");
        assertThat(stockService.getStockLedger(product.id(), 0, 25).content())
                .extracting("reasonCode")
                .containsExactly(StockReasonCode.PURCHASE, StockReasonCode.OPENING_STOCK);

        var dueSupplier = purchasingService.getSupplier(supplier.id());
        assertThat(dueSupplier.outstandingAmount()).isEqualByComparingTo("60.00");
        assertThat(purchasingService.searchSuppliers(
                "Fresh", true, SupplierBalanceStatus.DUE, 0, 25).content())
                .extracting("id")
                .containsExactly(supplier.id());
        assertThat(purchasingService.getSupplierStatement(supplier.id(), 0, 25).content())
                .extracting("entryType")
                .containsExactly(SupplierLedgerEntryType.PURCHASE_DUE);

        var paid = purchasingService.paySupplier(
                actor,
                supplier.id(),
                "payment-flow-1",
                new PurchasingRequests.SupplierPaymentRequest(
                        new BigDecimal("25.00"), SupplierPaymentMode.BANK_TRANSFER,
                        "BANK-001", "Partial payment", dueSupplier.balanceVersion()));
        assertThat(paid.balanceAfter()).isEqualByComparingTo("35.00");
        assertThat(paid.idempotentReplay()).isFalse();

        var replay = purchasingService.paySupplier(
                actor,
                supplier.id(),
                "payment-flow-1",
                new PurchasingRequests.SupplierPaymentRequest(
                        new BigDecimal("25.00"), SupplierPaymentMode.BANK_TRANSFER,
                        "BANK-001", "Partial payment", dueSupplier.balanceVersion()));
        assertThat(replay.entryId()).isEqualTo(paid.entryId());
        assertThat(replay.idempotentReplay()).isTrue();

        assertThat(purchasingService.getSummary().totalOutstanding()).isEqualByComparingTo("35.00");
        assertThat(purchasingService.searchPurchases(
                "FW-2026", supplier.id(), null, null, 0, 25).content())
                .extracting("id")
                .containsExactly(received.id());
        assertThat(purchasingService.getSupplierStatement(supplier.id(), 0, 25).content())
                .extracting("entryType")
                .containsExactly(
                        SupplierLedgerEntryType.PAYMENT,
                        SupplierLedgerEntryType.PURCHASE_DUE);
    }
}
