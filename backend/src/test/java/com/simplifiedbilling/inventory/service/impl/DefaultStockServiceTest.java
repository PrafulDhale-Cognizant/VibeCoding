package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.domain.InventoryBalance;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.ProductData;
import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.dto.StockAdjustmentRequest;
import com.simplifiedbilling.inventory.mapper.CategoryMapper;
import com.simplifiedbilling.inventory.mapper.ProductMapper;
import com.simplifiedbilling.inventory.repository.InventoryBalanceRepository;
import com.simplifiedbilling.inventory.repository.ProductRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultStockServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock
    private InventoryBalanceRepository balanceRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockTransactionRepository transactionRepository;
    @Mock
    private AuditWriter auditWriter;

    private ProductMapper productMapper;
    private DefaultStockService service;

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper(new CategoryMapper());
        service = new DefaultStockService(
                balanceRepository,
                productRepository,
                transactionRepository,
                productMapper,
                auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void adjustsStockUnderLockAndWritesImmutableLedger() {
        Product product = product(ProductUnit.PIECE, "5");
        InventoryBalance balance = product.getStockBalance();
        when(balanceRepository.findByProductIdForUpdate(product.getId()))
                .thenReturn(Optional.of(balance));

        var response = service.adjustStock(
                "actor",
                product.getId(),
                new StockAdjustmentRequest(
                        new BigDecimal("-2"),
                        StockReasonCode.DAMAGE,
                        " Damaged packs ",
                        0));

        assertThat(response.stockQuantity()).isEqualByComparingTo("3.000");
        ArgumentCaptor<StockTransaction> transaction = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(transaction.capture());
        assertThat(transaction.getValue().getTransactionType())
                .isEqualTo(StockTransactionType.ADJUSTMENT);
        assertThat(transaction.getValue().getQuantityDelta()).isEqualByComparingTo("-2.000");
        assertThat(transaction.getValue().getNotes()).isEqualTo("Damaged packs");
        verify(balanceRepository).flush();
        verify(auditWriter).write(
                "actor",
                "STOCK_ADJUSTED",
                "PRODUCT",
                product.getId(),
                Map.of(
                        "quantityDelta", new BigDecimal("-2.000"),
                        "reason", "DAMAGE"));
    }

    @Test
    void acceptsDecimalQuantityForLooseProductsAndBlankNotes() {
        Product product = product(ProductUnit.KILOGRAM, "5");
        when(balanceRepository.findByProductIdForUpdate(product.getId()))
                .thenReturn(Optional.of(product.getStockBalance()));

        var response = service.adjustStock(
                "actor",
                product.getId(),
                new StockAdjustmentRequest(
                        new BigDecimal("0.250"),
                        StockReasonCode.FOUND_STOCK,
                        " ",
                        0));

        assertThat(response.stockQuantity()).isEqualByComparingTo("5.250");
        ArgumentCaptor<StockTransaction> transaction = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(transaction.capture());
        assertThat(transaction.getValue().getNotes()).isNull();
    }

    @Test
    void rejectsInvalidReasonZeroAndExcessPrecision() {
        assertError(
                () -> service.adjustStock(
                        "actor",
                        "product",
                        new StockAdjustmentRequest(
                                BigDecimal.ONE,
                                StockReasonCode.OPENING_STOCK,
                                null,
                                0)),
                "INVALID_ADJUSTMENT_REASON");
        assertError(
                () -> service.adjustStock(
                        "actor",
                        "product",
                        new StockAdjustmentRequest(
                                BigDecimal.ONE,
                                StockReasonCode.SALE,
                                null,
                                0)),
                "INVALID_ADJUSTMENT_REASON");
        assertError(
                () -> service.adjustStock(
                        "actor",
                        "product",
                        new StockAdjustmentRequest(
                                BigDecimal.ONE,
                                StockReasonCode.SALE_RETURN,
                                null,
                                0)),
                "INVALID_ADJUSTMENT_REASON");
        assertError(
                () -> service.adjustStock(
                        "actor",
                        "product",
                        new StockAdjustmentRequest(
                                BigDecimal.ZERO,
                                StockReasonCode.OTHER,
                                null,
                                0)),
                "ZERO_STOCK_ADJUSTMENT");
        assertError(
                () -> service.adjustStock(
                        "actor",
                        "product",
                        new StockAdjustmentRequest(
                                new BigDecimal("1.0001"),
                                StockReasonCode.OTHER,
                                null,
                                0)),
                "INVALID_QUANTITY_PRECISION");
    }

    @Test
    void rejectsMissingStaleFractionalAndNegativeStock() {
        assertError(
                () -> service.adjustStock(
                        "actor",
                        "missing",
                        adjustment(BigDecimal.ONE, 0)),
                "PRODUCT_NOT_FOUND");

        Product product = product(ProductUnit.PIECE, "2");
        when(balanceRepository.findByProductIdForUpdate(product.getId()))
                .thenReturn(Optional.of(product.getStockBalance()));
        assertError(
                () -> service.adjustStock(
                        "actor",
                        product.getId(),
                        adjustment(BigDecimal.ONE, 5)),
                "STALE_STOCK_VERSION");
        assertError(
                () -> service.adjustStock(
                        "actor",
                        product.getId(),
                        adjustment(new BigDecimal("0.5"), 0)),
                "FRACTIONAL_QUANTITY_NOT_ALLOWED");
        assertError(
                () -> service.adjustStock(
                        "actor",
                        product.getId(),
                        adjustment(new BigDecimal("-3"), 0)),
                "INSUFFICIENT_STOCK");
    }

    @Test
    void pagesStockLedgerAndRejectsInvalidRequests() {
        Product product = product(ProductUnit.PIECE, "2");
        StockTransaction transaction = StockTransaction.create(
                product,
                StockTransactionType.OPENING,
                new BigDecimal("2.000"),
                new BigDecimal("2.000"),
                StockReasonCode.OPENING_STOCK,
                "PRODUCT",
                product.getId(),
                "Opening",
                "actor",
                NOW);
        when(productRepository.existsById(product.getId())).thenReturn(true);
        when(transactionRepository.findByProduct_IdOrderByOccurredAtDesc(
                product.getId(), PageRequest.of(0, 25)))
                .thenReturn(new PageImpl<>(List.of(transaction)));

        assertThat(service.getStockLedger(product.getId(), 0, 25).content())
                .extracting("reasonCode")
                .containsExactly(StockReasonCode.OPENING_STOCK);
        assertError(() -> service.getStockLedger(product.getId(), -1, 25), "INVALID_PAGE_REQUEST");
        assertError(() -> service.getStockLedger(product.getId(), 0, 101), "INVALID_PAGE_REQUEST");
        assertError(() -> service.getStockLedger("missing", 0, 25), "PRODUCT_NOT_FOUND");
    }

    private StockAdjustmentRequest adjustment(BigDecimal delta, long version) {
        return new StockAdjustmentRequest(delta, StockReasonCode.OTHER, null, version);
    }

    private Product product(ProductUnit unit, String stock) {
        Category category = Category.create("Grocery", NOW.minusSeconds(60));
        return Product.create(
                new ProductData(
                        "Rice",
                        "Rice",
                        "RICE-1",
                        unit,
                        "1006",
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ONE.setScale(2),
                        BigDecimal.TEN.setScale(2),
                        BigDecimal.ONE.setScale(3),
                        true),
                category,
                "1234",
                false,
                new BigDecimal(stock).setScale(3),
                NOW.minusSeconds(30));
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(code));
    }
}
