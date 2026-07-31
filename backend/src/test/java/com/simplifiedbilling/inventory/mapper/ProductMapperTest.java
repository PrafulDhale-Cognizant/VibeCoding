package com.simplifiedbilling.inventory.mapper;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.ProductData;
import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    private CategoryMapper categoryMapper;
    private ProductMapper mapper;

    @BeforeEach
    void setUp() {
        categoryMapper = new CategoryMapper();
        mapper = new ProductMapper(categoryMapper);
    }

    @Test
    void normalizesCreateAndUpdateData() {
        String longName = "A".repeat(90);
        ProductCreateRequest create = new ProductCreateRequest(
                " " + longName + " ",
                " ",
                " rice.1 ",
                " code-1 ",
                false,
                "category",
                ProductUnit.KILOGRAM,
                " 1006 ",
                new BigDecimal("5"),
                new BigDecimal("45"),
                new BigDecimal("50"),
                new BigDecimal("1"),
                new BigDecimal("2"));

        ProductData createData = mapper.toData(create);
        assertThat(createData.name()).isEqualTo(longName);
        assertThat(createData.receiptName()).hasSize(80);
        assertThat(createData.sku()).isEqualTo("RICE.1");
        assertThat(createData.hsnCode()).isEqualTo("1006");
        assertThat(createData.gstRate()).isEqualByComparingTo("5.00");
        assertThat(createData.minimumStockLevel()).isEqualByComparingTo("2.000");
        assertThat(createData.active()).isTrue();

        ProductUpdateRequest update = new ProductUpdateRequest(
                " Rice ",
                " Receipt Rice ",
                " ",
                "code-1",
                "category",
                ProductUnit.PACKET,
                "",
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                false,
                0);
        ProductData updateData = mapper.toData(update);
        assertThat(updateData.receiptName()).isEqualTo("Receipt Rice");
        assertThat(updateData.sku()).isNull();
        assertThat(updateData.hsnCode()).isNull();
        assertThat(updateData.active()).isFalse();
        assertThat(mapper.normalizeBarcode(" code-1 ")).isEqualTo("CODE-1");
        assertThat(mapper.normalizeSku(null)).isNull();
    }

    @Test
    void mapsProductLookupAlertsUnitsAndStockStates() {
        Category category = Category.create("Grocery", NOW);
        Product product = product(category, new BigDecimal("2.000"), new BigDecimal("3.000"));

        var response = mapper.toResponse(product);
        assertThat(response.name()).isEqualTo("Rice");
        assertThat(response.category().name()).isEqualTo("Grocery");
        assertThat(response.stockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(response.stockVersion()).isZero();

        var lookup = mapper.toLookupResponse(product);
        assertThat(lookup.barcode()).isEqualTo("1234");
        assertThat(lookup.sellingPrice()).isEqualByComparingTo("50.00");

        var alert = mapper.toAlertResponse(product);
        assertThat(alert.suggestedReorderQuantity()).isEqualByComparingTo("1.000");
        assertThat(alert.stockStatus()).isEqualTo(StockStatus.LOW_STOCK);

        assertThat(mapper.stockStatus(BigDecimal.ZERO, BigDecimal.TEN))
                .isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(mapper.stockStatus(BigDecimal.TEN, BigDecimal.ONE))
                .isEqualTo(StockStatus.IN_STOCK);
        assertThat(mapper.quantity(new BigDecimal("1.2"))).isEqualByComparingTo("1.200");

        var unit = mapper.toUnitResponse(ProductUnit.LITRE);
        assertThat(unit.displayName()).isEqualTo("Litre");
        assertThat(unit.decimalAllowed()).isTrue();
    }

    @Test
    void mapsTransactionsAndPages() {
        Category category = Category.create("Grocery", NOW);
        Product product = product(category, BigDecimal.ONE, BigDecimal.ZERO);
        StockTransaction transaction = StockTransaction.create(
                product,
                StockTransactionType.OPENING,
                BigDecimal.ONE,
                BigDecimal.ONE,
                StockReasonCode.OPENING_STOCK,
                "PRODUCT",
                product.getId(),
                "Opening",
                "actor",
                NOW);

        var mapped = mapper.toTransactionResponse(transaction);
        assertThat(mapped.productId()).isEqualTo(product.getId());
        assertThat(mapped.reasonCode()).isEqualTo(StockReasonCode.OPENING_STOCK);

        PageImpl<Product> source = new PageImpl<>(
                List.of(product),
                PageRequest.of(0, 1),
                2);
        InventoryPage<String> page = InventoryPage.from(source, Product::getName);
        assertThat(page.content()).containsExactly("Rice");
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isFalse();
    }

    private Product product(Category category, BigDecimal stock, BigDecimal minimum) {
        return Product.create(
                new ProductData(
                        "Rice",
                        "Rice",
                        "RICE-1",
                        ProductUnit.KILOGRAM,
                        "1006",
                        new BigDecimal("5.00"),
                        new BigDecimal("45.00"),
                        new BigDecimal("50.00"),
                        minimum,
                        true),
                category,
                "1234",
                false,
                stock,
                NOW);
    }
}
