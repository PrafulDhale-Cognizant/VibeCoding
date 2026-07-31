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
import com.simplifiedbilling.inventory.service.PurchaseReturnStockRequest;
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
class DefaultPurchaseReturnInventoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Mock private InventoryBalanceRepository balanceRepository;
    @Mock private StockTransactionRepository transactionRepository;
    private DefaultPurchaseReturnInventoryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultPurchaseReturnInventoryService(
                balanceRepository, transactionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsStockUnderOrderedLockAndWritesImmutableLedger() {
        Product rice = product("Rice", ProductUnit.KILOGRAM, "5");
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(rice.getId())))
                .thenReturn(List.of(rice.getStockBalance()));

        service.returnToSupplier(
                "actor", "return-1", List.of(new PurchaseReturnStockRequest(
                        rice.getId(), rice.getName(), new BigDecimal("2.500"))));

        assertThat(rice.getStockBalance().getQuantity()).isEqualByComparingTo("2.500");
        ArgumentCaptor<StockTransaction> ledger = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(ledger.capture());
        assertThat(ledger.getValue().getTransactionType())
                .isEqualTo(StockTransactionType.PURCHASE_RETURN);
        assertThat(ledger.getValue().getReasonCode()).isEqualTo(StockReasonCode.PURCHASE_RETURN);
        assertThat(ledger.getValue().getQuantityDelta()).isEqualByComparingTo("-2.500");
        assertThat(ledger.getValue().getBalanceAfter()).isEqualByComparingTo("2.500");
        assertThat(ledger.getValue().getReferenceType()).isEqualTo("PURCHASE_RETURN");
        assertThat(ledger.getValue().getReferenceId()).isEqualTo("return-1");
        verify(balanceRepository).flush();
    }

    @Test
    void validatesShapeQuantityProductStockAndUnitRules() {
        assertError(() -> service.returnToSupplier("actor", "return", List.of()),
                "EMPTY_PURCHASE_RETURN");
        assertError(() -> service.returnToSupplier(
                "actor", "return", java.util.Arrays.asList((PurchaseReturnStockRequest) null)),
                "PRODUCT_NOT_FOUND");
        assertError(() -> service.returnToSupplier("actor", "return", List.of(
                new PurchaseReturnStockRequest("same", "Rice", BigDecimal.ONE),
                new PurchaseReturnStockRequest("same", "Rice", BigDecimal.ONE))),
                "DUPLICATE_RETURN_PRODUCT");
        assertError(() -> service.returnToSupplier("actor", "return", List.of(
                new PurchaseReturnStockRequest("product", "Rice", BigDecimal.ZERO))),
                "INVALID_RETURN_QUANTITY");
        assertError(() -> service.returnToSupplier("actor", "return", List.of(
                new PurchaseReturnStockRequest("product", "Rice", new BigDecimal("1.0001")))),
                "INVALID_QUANTITY_PRECISION");
        when(balanceRepository.findAllByProductIdsForUpdate(List.of("missing"))).thenReturn(List.of());
        assertError(() -> service.returnToSupplier("actor", "return", List.of(
                new PurchaseReturnStockRequest("missing", "Rice", BigDecimal.ONE))),
                "PRODUCT_NOT_FOUND");

        Product lowStock = product("Low", ProductUnit.PIECE, "1");
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(lowStock.getId())))
                .thenReturn(List.of(lowStock.getStockBalance()));
        assertError(() -> service.returnToSupplier("actor", "return", List.of(
                new PurchaseReturnStockRequest(lowStock.getId(), "Low", new BigDecimal("2")))),
                "INSUFFICIENT_RETURN_STOCK");

        Product piece = product("Packet", ProductUnit.PIECE, "5");
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(piece.getId())))
                .thenReturn(List.of(piece.getStockBalance()));
        assertError(() -> service.returnToSupplier("actor", "return", List.of(
                new PurchaseReturnStockRequest(piece.getId(), "Packet", new BigDecimal("0.500")))),
                "FRACTIONAL_QUANTITY_NOT_ALLOWED");
    }

    private Product product(String name, ProductUnit unit, String stock) {
        return Product.create(
                new ProductData(
                        name, name, name.toUpperCase(), unit, "1006", new BigDecimal("5.00"),
                        new BigDecimal("8.00"), new BigDecimal("10.00"),
                        BigDecimal.ONE.setScale(3), true),
                Category.create("Grocery", NOW.minusSeconds(60)), "1234", false,
                new BigDecimal(stock).setScale(3), NOW.minusSeconds(30));
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
