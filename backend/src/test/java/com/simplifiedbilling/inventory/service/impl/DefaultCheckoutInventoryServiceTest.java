package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.ProductData;
import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.repository.InventoryBalanceRepository;
import com.simplifiedbilling.inventory.repository.ProductRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.inventory.service.SaleStockRequest;
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
class DefaultCheckoutInventoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock private ProductRepository productRepository;
    @Mock private InventoryBalanceRepository balanceRepository;
    @Mock private StockTransactionRepository transactionRepository;
    private DefaultCheckoutInventoryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCheckoutInventoryService(
                productRepository,
                balanceRepository,
                transactionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void getsActiveProductsInRequestedOrder() {
        Product rice = product("Rice", ProductUnit.PIECE, "4", true);
        Product oil = product("Oil", ProductUnit.LITRE, "2", true);
        when(productRepository.findDetailedByIdIn(List.of(oil.getId(), rice.getId())))
                .thenReturn(List.of(rice, oil));

        var products = service.getSaleProducts(List.of(oil.getId(), rice.getId()));

        assertThat(products).extracting("productId")
                .containsExactly(oil.getId(), rice.getId());
        assertThat(products.getFirst().name()).isEqualTo("Oil");
        assertThat(products.getFirst().barcode()).isEqualTo("1234");
        assertThat(products.getFirst().availableQuantity()).isEqualByComparingTo("2.000");
    }

    @Test
    void deductsStockUnderOneOrderedLockAndWritesSaleLedger() {
        Product product = product("Rice", ProductUnit.PIECE, "5", true);
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(product.getId())))
                .thenReturn(List.of(product.getStockBalance()));

        var result = service.deductForSale(
                "actor",
                "invoice-1",
                List.of(new SaleStockRequest(product.getId(), new BigDecimal("2"))));

        assertThat(result.getFirst().availableQuantity()).isEqualByComparingTo("5.000");
        ArgumentCaptor<StockTransaction> ledger = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(ledger.capture());
        assertThat(ledger.getValue().getTransactionType()).isEqualTo(StockTransactionType.SALE);
        assertThat(ledger.getValue().getReasonCode()).isEqualTo(StockReasonCode.SALE);
        assertThat(ledger.getValue().getQuantityDelta()).isEqualByComparingTo("-2.000");
        assertThat(ledger.getValue().getBalanceAfter()).isEqualByComparingTo("3.000");
        assertThat(ledger.getValue().getReferenceType()).isEqualTo("INVOICE");
        assertThat(ledger.getValue().getReferenceId()).isEqualTo("invoice-1");
        assertThat(ledger.getValue().getActorUserId()).isEqualTo("actor");
        verify(balanceRepository).flush();
    }

    @Test
    void validatesLookupInputMissingAndInactiveProducts() {
        assertError(() -> service.getSaleProducts(List.of()), "EMPTY_CART");
        assertError(() -> service.getSaleProducts(List.of("same", "same")), "DUPLICATE_CART_PRODUCT");
        assertError(() -> service.getSaleProducts(java.util.Arrays.asList((String) null)), "PRODUCT_NOT_FOUND");

        when(productRepository.findDetailedByIdIn(List.of("missing"))).thenReturn(List.of());
        assertError(() -> service.getSaleProducts(List.of("missing")), "PRODUCT_NOT_FOUND");

        Product inactive = product("Old rice", ProductUnit.PIECE, "1", false);
        when(productRepository.findDetailedByIdIn(List.of(inactive.getId()))).thenReturn(List.of(inactive));
        assertError(() -> service.getSaleProducts(List.of(inactive.getId())), "INACTIVE_PRODUCT");
    }

    @Test
    void validatesDeductionCartShapeAndQuantities() {
        assertError(() -> service.deductForSale("actor", "invoice", List.of()), "EMPTY_CART");
        assertError(() -> service.deductForSale("actor", "invoice", java.util.Arrays.asList((SaleStockRequest) null)), "PRODUCT_NOT_FOUND");
        assertError(() -> service.deductForSale("actor", "invoice", List.of(
                new SaleStockRequest("same", BigDecimal.ONE),
                new SaleStockRequest("same", BigDecimal.ONE))), "DUPLICATE_CART_PRODUCT");
        assertError(() -> service.deductForSale("actor", "invoice", List.of(
                new SaleStockRequest("product", BigDecimal.ZERO))), "INVALID_QUANTITY");
        assertError(() -> service.deductForSale("actor", "invoice", List.of(
                new SaleStockRequest("product", new BigDecimal("1.0001")))), "INVALID_QUANTITY_PRECISION");

        when(balanceRepository.findAllByProductIdsForUpdate(List.of("missing"))).thenReturn(List.of());
        assertError(() -> service.deductForSale("actor", "invoice", List.of(
                new SaleStockRequest("missing", BigDecimal.ONE))), "PRODUCT_NOT_FOUND");
    }

    @Test
    void rejectsInactiveFractionalAndInsufficientProducts() {
        Product inactive = product("Old", ProductUnit.PIECE, "2", false);
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(inactive.getId())))
                .thenReturn(List.of(inactive.getStockBalance()));
        assertError(() -> service.deductForSale("actor", "invoice", List.of(
                new SaleStockRequest(inactive.getId(), BigDecimal.ONE))), "INACTIVE_PRODUCT");

        Product piece = product("Packet", ProductUnit.PIECE, "2", true);
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(piece.getId())))
                .thenReturn(List.of(piece.getStockBalance()));
        assertError(() -> service.deductForSale("actor", "invoice", List.of(
                new SaleStockRequest(piece.getId(), new BigDecimal("0.500")))), "FRACTIONAL_QUANTITY_NOT_ALLOWED");
        assertError(() -> service.deductForSale("actor", "invoice", List.of(
                new SaleStockRequest(piece.getId(), new BigDecimal("3")))), "INSUFFICIENT_STOCK");
    }

    @Test
    void acceptsDecimalLooseItemQuantity() {
        Product loose = product("Flour", ProductUnit.KILOGRAM, "2", true);
        when(balanceRepository.findAllByProductIdsForUpdate(List.of(loose.getId())))
                .thenReturn(List.of(loose.getStockBalance()));

        var result = service.deductForSale("actor", "invoice", List.of(
                new SaleStockRequest(loose.getId(), new BigDecimal("0.250"))));

        assertThat(result.getFirst().availableQuantity()).isEqualByComparingTo("2.000");
    }

    private Product product(String name, ProductUnit unit, String stock, boolean active) {
        return Product.create(
                new ProductData(
                        name, name, name.toUpperCase(), unit, "1006",
                        new BigDecimal("5.00"), new BigDecimal("8.00"),
                        new BigDecimal("10.00"), BigDecimal.ONE.setScale(3), active),
                Category.create("Grocery", NOW.minusSeconds(60)),
                "1234",
                false,
                new BigDecimal(stock).setScale(3),
                NOW.minusSeconds(30));
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
