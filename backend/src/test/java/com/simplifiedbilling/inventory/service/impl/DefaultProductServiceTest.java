package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.ProductData;
import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductUpdateRequest;
import com.simplifiedbilling.inventory.mapper.CategoryMapper;
import com.simplifiedbilling.inventory.mapper.ProductMapper;
import com.simplifiedbilling.inventory.repository.CategoryRepository;
import com.simplifiedbilling.inventory.repository.ProductBarcodeRepository;
import com.simplifiedbilling.inventory.repository.ProductRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.inventory.service.InternalBarcodeAllocator;
import com.simplifiedbilling.inventory.service.ProductSearch;
import com.simplifiedbilling.inventory.service.ProductSort;
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
import org.springframework.data.domain.Pageable;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductBarcodeRepository barcodeRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private StockTransactionRepository transactionRepository;
    @Mock
    private InternalBarcodeAllocator barcodeAllocator;
    @Mock
    private AuditWriter auditWriter;

    private ProductMapper productMapper;
    private DefaultProductService service;

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper(new CategoryMapper());
        service = new DefaultProductService(
                productRepository,
                barcodeRepository,
                categoryRepository,
                transactionRepository,
                barcodeAllocator,
                productMapper,
                auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void searchesWithEverySupportedSortAndMapsResults() {
        Category category = category("Grocery");
        Product product = product(category, ProductUnit.KILOGRAM, "1234", false, "2.000", "3.000");
        when(productRepository.search(
                eq("%rice%"),
                eq(category.getId()),
                eq(true),
                eq("LOW_STOCK"),
                any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(product),
                        invocation.getArgument(4),
                        1));

        for (ProductSort sort : ProductSort.values()) {
            var result = service.searchProducts(new ProductSearch(
                    " Rice ", category.getId(), true, StockStatus.LOW_STOCK, 0, 25, sort));
            assertThat(result.content()).extracting("name").containsExactly("Rice");
        }
        verify(productRepository, times(ProductSort.values().length)).search(
                eq("%rice%"),
                eq(category.getId()),
                eq(true),
                eq("LOW_STOCK"),
                any(Pageable.class));

        when(productRepository.search(
                isNull(), isNull(), isNull(), eq("ALL"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        assertThat(service.searchProducts(new ProductSearch(
                " ", " ", null, null, 0, 10, null)).content()).hasSize(1);

        assertError(
                () -> service.searchProducts(new ProductSearch(
                        null, null, true, StockStatus.ALL, -1, 25, ProductSort.NAME_ASC)),
                "INVALID_PAGE_REQUEST");
        assertError(
                () -> service.searchProducts(new ProductSearch(
                        null, null, true, StockStatus.ALL, 0, 101, ProductSort.NAME_ASC)),
                "INVALID_PAGE_REQUEST");
    }

    @Test
    void getsProductLooksUpBarcodeAndListsUnits() {
        Product product = product(category("Grocery"), ProductUnit.PIECE, "ABC-1", false, "5.000", "1.000");
        when(productRepository.findDetailedById(product.getId())).thenReturn(Optional.of(product));
        when(productRepository.findDetailedByBarcode("ABC-1")).thenReturn(Optional.of(product));

        assertThat(service.getProduct(product.getId()).name()).isEqualTo("Rice");
        assertThat(service.findByBarcode(" abc-1 ").id()).isEqualTo(product.getId());
        assertThat(service.listUnits()).hasSize(ProductUnit.values().length);

        assertError(() -> service.getProduct("missing"), "PRODUCT_NOT_FOUND");
        assertError(() -> service.findByBarcode("missing"), "BARCODE_NOT_FOUND");
    }

    @Test
    void createsManualProductAndOpeningLedgerEntry() {
        Category category = category("Grocery");
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        var response = service.createProduct(
                "actor",
                createRequest(category.getId(), " rice-1 ", " 1234 ", false,
                        ProductUnit.PIECE, "2", "1"));

        assertThat(response.name()).isEqualTo("Rice");
        assertThat(response.sku()).isEqualTo("RICE-1");
        assertThat(response.barcode()).isEqualTo("1234");
        assertThat(response.internalBarcode()).isFalse();
        assertThat(response.stockQuantity()).isEqualByComparingTo("2.000");
        verify(productRepository).saveAndFlush(any(Product.class));

        ArgumentCaptor<StockTransaction> transaction = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(transaction.capture());
        assertThat(transaction.getValue().getBalanceAfter()).isEqualByComparingTo("2.000");
        verify(auditWriter).write(
                "actor",
                "PRODUCT_CREATED",
                "PRODUCT",
                response.id(),
                Map.of("name", "Rice", "barcode", "1234"));
    }

    @Test
    void createsGeneratedBarcodeProductWithoutZeroOpeningEntry() {
        Category category = category("Grocery");
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(barcodeAllocator.allocate()).thenReturn("2000000000015");

        var response = service.createProduct(
                "actor",
                createRequest(category.getId(), null, null, true,
                        ProductUnit.KILOGRAM, "0", "1.5"));

        assertThat(response.barcode()).isEqualTo("2000000000015");
        assertThat(response.internalBarcode()).isTrue();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidCategoryDuplicatesAndQuantityRulesOnCreate() {
        assertError(
                () -> service.createProduct(
                        "actor",
                        createRequest("missing", null, "1234", false,
                                ProductUnit.PIECE, "0", "0")),
                "CATEGORY_NOT_FOUND");

        Category inactive = category("Inactive");
        inactive.update("Inactive", false, NOW);
        when(categoryRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        assertError(
                () -> service.createProduct(
                        "actor",
                        createRequest(inactive.getId(), null, "1234", false,
                                ProductUnit.PIECE, "0", "0")),
                "CATEGORY_INACTIVE");

        Category category = category("Grocery");
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.existsBySkuIgnoreCase("DUPLICATE")).thenReturn(true);
        assertError(
                () -> service.createProduct(
                        "actor",
                        createRequest(category.getId(), "duplicate", "1234", false,
                                ProductUnit.PIECE, "0", "0")),
                "SKU_EXISTS");

        when(productRepository.existsBySkuIgnoreCase("DUPLICATE")).thenReturn(false);
        when(barcodeRepository.existsByValue("1234")).thenReturn(true);
        assertError(
                () -> service.createProduct(
                        "actor",
                        createRequest(category.getId(), "duplicate", "1234", false,
                                ProductUnit.PIECE, "0", "0")),
                "BARCODE_EXISTS");

        assertError(
                () -> service.createProduct(
                        "actor",
                        createRequest(category.getId(), null, "fraction", false,
                                ProductUnit.PIECE, "1.5", "0")),
                "FRACTIONAL_QUANTITY_NOT_ALLOWED");
        assertError(
                () -> service.createProduct(
                        "actor",
                        createRequest(category.getId(), null, "precision", false,
                                ProductUnit.KILOGRAM, "1.0001", "0")),
                "INVALID_QUANTITY_PRECISION");
    }

    @Test
    void updatesProductAndPreservesInternalFlagOnlyForSameBarcode() {
        Category category = category("Grocery");
        Product product = product(
                category,
                ProductUnit.KILOGRAM,
                "2000000000015",
                true,
                "2.000",
                "1.000");
        when(productRepository.findDetailedById(product.getId())).thenReturn(Optional.of(product));
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        var sameBarcode = service.updateProduct(
                "actor",
                product.getId(),
                updateRequest(category.getId(), "2000000000015", ProductUnit.KILOGRAM, true, 0));
        assertThat(sameBarcode.internalBarcode()).isTrue();

        var changedBarcode = service.updateProduct(
                "actor",
                product.getId(),
                updateRequest(category.getId(), "MANUAL-1", ProductUnit.KILOGRAM, false, 0));
        assertThat(changedBarcode.internalBarcode()).isFalse();
        assertThat(changedBarcode.active()).isFalse();
        verify(productRepository, times(2)).flush();
    }

    @Test
    void rejectsStaleAndDuplicateProductUpdates() {
        Category category = category("Grocery");
        Product product = product(category, ProductUnit.PIECE, "1234", false, "2.000", "1.000");
        when(productRepository.findDetailedById(product.getId())).thenReturn(Optional.of(product));

        assertError(
                () -> service.updateProduct(
                        "actor",
                        product.getId(),
                        updateRequest(category.getId(), "1234", ProductUnit.PIECE, true, 99)),
                "STALE_PRODUCT_VERSION");

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.existsBySkuIgnoreCaseAndIdNot("RICE-1", product.getId()))
                .thenReturn(true);
        assertError(
                () -> service.updateProduct(
                        "actor",
                        product.getId(),
                        updateRequest(category.getId(), "1234", ProductUnit.PIECE, true, 0)),
                "SKU_EXISTS");

        when(productRepository.existsBySkuIgnoreCaseAndIdNot("RICE-1", product.getId()))
                .thenReturn(false);
        when(barcodeRepository.existsByValueAndProduct_IdNot("1234", product.getId()))
                .thenReturn(true);
        assertError(
                () -> service.updateProduct(
                        "actor",
                        product.getId(),
                        updateRequest(category.getId(), "1234", ProductUnit.PIECE, true, 0)),
                "BARCODE_EXISTS");
    }

    @Test
    void returnsOnlySupportedStockAlerts() {
        Product product = product(category("Grocery"), ProductUnit.PIECE, "1234", false, "0", "2");
        when(productRepository.search(
                isNull(), isNull(), eq(true), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 25), 1));

        assertThat(service.getStockAlerts(StockStatus.LOW_STOCK, 0, 25).content()).hasSize(1);
        assertThat(service.getStockAlerts(StockStatus.OUT_OF_STOCK, 0, 25).content()).hasSize(1);
        assertError(
                () -> service.getStockAlerts(StockStatus.ALL, 0, 25),
                "INVALID_STOCK_ALERT_STATUS");
    }

    private Category category(String name) {
        return Category.create(name, NOW.minusSeconds(60));
    }

    private Product product(
            Category category,
            ProductUnit unit,
            String barcode,
            boolean internal,
            String stock,
            String minimum) {
        return Product.create(
                new ProductData(
                        "Rice",
                        "Rice",
                        "RICE-1",
                        unit,
                        "1006",
                        new BigDecimal("5.00"),
                        new BigDecimal("45.00"),
                        new BigDecimal("50.00"),
                        new BigDecimal(minimum).setScale(3),
                        true),
                category,
                barcode,
                internal,
                new BigDecimal(stock).setScale(3),
                NOW.minusSeconds(30));
    }

    private ProductCreateRequest createRequest(
            String categoryId,
            String sku,
            String barcode,
            boolean generated,
            ProductUnit unit,
            String opening,
            String minimum) {
        return new ProductCreateRequest(
                " Rice ",
                " ",
                sku,
                barcode,
                generated,
                categoryId,
                unit,
                "1006",
                new BigDecimal("5.00"),
                new BigDecimal("45.00"),
                new BigDecimal("50.00"),
                new BigDecimal(opening),
                new BigDecimal(minimum));
    }

    private ProductUpdateRequest updateRequest(
            String categoryId,
            String barcode,
            ProductUnit unit,
            boolean active,
            long version) {
        return new ProductUpdateRequest(
                "Rice",
                "Rice",
                "RICE-1",
                barcode,
                categoryId,
                unit,
                "1006",
                new BigDecimal("5.00"),
                new BigDecimal("45.00"),
                new BigDecimal("50.00"),
                BigDecimal.ONE,
                active,
                version);
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(code));
    }
}
