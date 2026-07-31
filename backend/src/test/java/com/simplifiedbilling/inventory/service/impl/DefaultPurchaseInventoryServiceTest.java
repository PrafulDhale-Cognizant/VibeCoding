package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.ProductData;
import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.repository.InventoryBalanceRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.inventory.service.PurchaseStockRequest;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPurchaseInventoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Mock private InventoryBalanceRepository balanceRepository;
    @Mock private StockTransactionRepository transactionRepository;
    private DefaultPurchaseInventoryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultPurchaseInventoryService(
                balanceRepository, transactionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void receivesStockUnderOrderedLockUpdatesCostAndWritesLedger() {
        Product rice = product("Rice", ProductUnit.PIECE, "5", true);
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(rice.getId())))
                .thenReturn(List.of(rice.getStockBalance()));

        var result = service.receivePurchase(
                "actor", "purchase-1", List.of(new PurchaseStockRequest(
                        rice.getId(), new BigDecimal("3"), new BigDecimal("9.50"))));

        assertThat(result).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.productId()).isEqualTo(rice.getId());
            assertThat(snapshot.name()).isEqualTo("Rice");
            assertThat(snapshot.quantity()).isEqualByComparingTo("3.000");
            assertThat(snapshot.unitCost()).isEqualByComparingTo("9.50");
        });
        assertThat(rice.getStockBalance().getQuantity()).isEqualByComparingTo("8.000");
        assertThat(rice.getPurchaseCost()).isEqualByComparingTo("9.50");
        ArgumentCaptor<StockTransaction> ledger = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(ledger.capture());
        assertThat(ledger.getValue().getTransactionType()).isEqualTo(StockTransactionType.PURCHASE);
        assertThat(ledger.getValue().getReasonCode()).isEqualTo(StockReasonCode.PURCHASE);
        assertThat(ledger.getValue().getQuantityDelta()).isEqualByComparingTo("3.000");
        assertThat(ledger.getValue().getBalanceAfter()).isEqualByComparingTo("8.000");
        assertThat(ledger.getValue().getReferenceType()).isEqualTo("PURCHASE");
        assertThat(ledger.getValue().getReferenceId()).isEqualTo("purchase-1");
        verify(balanceRepository).flush();
    }

    @Test
    void validatesShapeQuantityCostAndMissingProducts() {
        assertError(() -> service.receivePurchase("actor", "purchase", List.of()), "EMPTY_PURCHASE");
        assertError(() -> service.receivePurchase(
                "actor", "purchase", java.util.Arrays.asList((PurchaseStockRequest) null)), "PRODUCT_NOT_FOUND");
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest("same", BigDecimal.ONE, BigDecimal.ONE),
                new PurchaseStockRequest("same", BigDecimal.ONE, BigDecimal.ONE))), "DUPLICATE_PURCHASE_PRODUCT");
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest("product", BigDecimal.ZERO, BigDecimal.ONE))), "INVALID_QUANTITY");
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest("product", new BigDecimal("1.0001"), BigDecimal.ONE))), "INVALID_QUANTITY_PRECISION");
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest("product", BigDecimal.ONE, null))), "INVALID_PURCHASE_COST");
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest("product", BigDecimal.ONE, BigDecimal.ZERO))), "INVALID_PURCHASE_COST");
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest("product", BigDecimal.ONE, new BigDecimal("1.001")))), "INVALID_MONEY_PRECISION");
        when(balanceRepository.findAllByProductIdsForUpdate(List.of("missing"))).thenReturn(List.of());
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest("missing", BigDecimal.ONE, BigDecimal.ONE))), "PRODUCT_NOT_FOUND");
    }

    @Test
    void rejectsInactiveAndFractionalPieceButAcceptsLooseQuantity() {
        Product inactive = product("Old", ProductUnit.PIECE, "1", false);
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(inactive.getId())))
                .thenReturn(List.of(inactive.getStockBalance()));
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest(inactive.getId(), BigDecimal.ONE, BigDecimal.ONE))), "INACTIVE_PRODUCT");

        Product piece = product("Packet", ProductUnit.PIECE, "1", true);
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(piece.getId())))
                .thenReturn(List.of(piece.getStockBalance()));
        assertError(() -> service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest(piece.getId(), new BigDecimal("0.500"), BigDecimal.ONE))),
                "FRACTIONAL_QUANTITY_NOT_ALLOWED");

        Product loose = product("Flour", ProductUnit.KILOGRAM, "1", true);
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(loose.getId())))
                .thenReturn(List.of(loose.getStockBalance()));
        assertThat(service.receivePurchase("actor", "purchase", List.of(
                new PurchaseStockRequest(loose.getId(), new BigDecimal("0.500"), BigDecimal.ONE))))
                .hasSize(1);
    }

    private Product product(String name, ProductUnit unit, String stock, boolean active) {
        return Product.create(
                new ProductData(
                        name, name, name.toUpperCase(), unit, "1006", new BigDecimal("5.00"),
                        new BigDecimal("8.00"), new BigDecimal("10.00"),
                        BigDecimal.ONE.setScale(3), active),
                Category.create("Grocery", NOW.minusSeconds(60)), "1234", false,
                new BigDecimal(stock).setScale(3), NOW.minusSeconds(30));
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
