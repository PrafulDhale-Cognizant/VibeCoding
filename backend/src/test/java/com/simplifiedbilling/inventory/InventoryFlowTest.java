package com.simplifiedbilling.inventory;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.dto.CategoryCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.dto.StockAdjustmentRequest;
import com.simplifiedbilling.inventory.service.CategoryService;
import com.simplifiedbilling.inventory.service.ProductSearch;
import com.simplifiedbilling.inventory.service.ProductService;
import com.simplifiedbilling.inventory.service.ProductSort;
import com.simplifiedbilling.inventory.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class InventoryFlowTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StockService stockService;

    @Test
    void persistsSearchesAndAdjustsInventoryWithLedgerHistory() {
        String actor = "inventory-test-actor";
        var category = categoryService.createCategory(
                actor,
                new CategoryCreateRequest("Loose Grocery"));
        var created = productService.createProduct(
                actor,
                new ProductCreateRequest(
                        "Basmati Rice",
                        "Basmati Rice",
                        "RICE-BAS-1",
                        "8901234567890",
                        false,
                        category.id(),
                        ProductUnit.KILOGRAM,
                        "1006",
                        new BigDecimal("5.00"),
                        new BigDecimal("70.00"),
                        new BigDecimal("85.00"),
                        new BigDecimal("10.000"),
                        new BigDecimal("5.000")));

        var search = productService.searchProducts(new ProductSearch(
                "basmati",
                category.id(),
                true,
                StockStatus.IN_STOCK,
                0,
                25,
                ProductSort.NAME_ASC));
        assertThat(search.content()).extracting("id").containsExactly(created.id());
        assertThat(productService.findByBarcode("8901234567890").id()).isEqualTo(created.id());

        var adjusted = stockService.adjustStock(
                actor,
                created.id(),
                new StockAdjustmentRequest(
                        new BigDecimal("-6.000"),
                        StockReasonCode.PHYSICAL_COUNT,
                        "Physical count correction",
                        created.stockVersion()));
        assertThat(adjusted.stockQuantity()).isEqualByComparingTo("4.000");
        assertThat(adjusted.stockStatus()).isEqualTo(StockStatus.LOW_STOCK);

        assertThat(stockService.getStockLedger(created.id(), 0, 25).content())
                .extracting("reasonCode")
                .containsExactly(StockReasonCode.PHYSICAL_COUNT, StockReasonCode.OPENING_STOCK);
        assertThat(productService.getStockAlerts(StockStatus.LOW_STOCK, 0, 25).content())
                .extracting("productId")
                .containsExactly(created.id());
    }
}
