package com.simplifiedbilling.inventory.controller;

import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.dto.BarcodeResponse;
import com.simplifiedbilling.inventory.dto.CategoryCreateRequest;
import com.simplifiedbilling.inventory.dto.CategoryResponse;
import com.simplifiedbilling.inventory.dto.CategoryUpdateRequest;
import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductAlertResponse;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductLookupResponse;
import com.simplifiedbilling.inventory.dto.ProductResponse;
import com.simplifiedbilling.inventory.dto.ProductUpdateRequest;
import com.simplifiedbilling.inventory.dto.StockAdjustmentRequest;
import com.simplifiedbilling.inventory.dto.StockTransactionResponse;
import com.simplifiedbilling.inventory.dto.UnitResponse;
import com.simplifiedbilling.inventory.service.BarcodeService;
import com.simplifiedbilling.inventory.service.CategoryService;
import com.simplifiedbilling.inventory.service.ProductSearch;
import com.simplifiedbilling.inventory.service.ProductService;
import com.simplifiedbilling.inventory.service.ProductSort;
import com.simplifiedbilling.inventory.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryControllersTest {

    @Test
    void categoryControllerDelegatesAllOperations() {
        CategoryService service = mock(CategoryService.class);
        CategoryController controller = new CategoryController(service);
        Jwt jwt = jwt();
        CategoryCreateRequest create = mock(CategoryCreateRequest.class);
        CategoryUpdateRequest update = mock(CategoryUpdateRequest.class);
        CategoryResponse response = mock(CategoryResponse.class);
        when(service.listCategories(true)).thenReturn(List.of(response));
        when(service.createCategory("actor", create)).thenReturn(response);
        when(service.updateCategory("actor", "category", update)).thenReturn(response);

        assertThat(controller.listCategories(true)).containsExactly(response);
        assertThat(controller.createCategory(jwt, create)).isSameAs(response);
        assertThat(controller.updateCategory(jwt, "category", update)).isSameAs(response);
    }

    @Test
    void productControllerDelegatesCatalogLookupAndAlerts() {
        ProductService service = mock(ProductService.class);
        ProductController controller = new ProductController(service);
        Jwt jwt = jwt();
        ProductCreateRequest create = mock(ProductCreateRequest.class);
        ProductUpdateRequest update = mock(ProductUpdateRequest.class);
        ProductResponse product = mock(ProductResponse.class);
        ProductLookupResponse lookup = mock(ProductLookupResponse.class);
        @SuppressWarnings("unchecked")
        InventoryPage<ProductResponse> page = mock(InventoryPage.class);
        @SuppressWarnings("unchecked")
        InventoryPage<ProductAlertResponse> alerts = mock(InventoryPage.class);
        ProductSearch search = new ProductSearch(
                "rice",
                "category",
                true,
                StockStatus.LOW_STOCK,
                1,
                20,
                ProductSort.UPDATED_DESC);
        when(service.searchProducts(search)).thenReturn(page);
        when(service.getProduct("product")).thenReturn(product);
        when(service.findByBarcode("1234")).thenReturn(lookup);
        when(service.createProduct("actor", create)).thenReturn(product);
        when(service.updateProduct("actor", "product", update)).thenReturn(product);
        when(service.getStockAlerts(StockStatus.OUT_OF_STOCK, 0, 10)).thenReturn(alerts);

        assertThat(controller.searchProducts(
                "rice", "category", true, StockStatus.LOW_STOCK,
                1, 20, ProductSort.UPDATED_DESC)).isSameAs(page);
        assertThat(controller.getProduct("product")).isSameAs(product);
        assertThat(controller.findByBarcode("1234")).isSameAs(lookup);
        assertThat(controller.createProduct(jwt, create)).isSameAs(product);
        assertThat(controller.updateProduct(jwt, "product", update)).isSameAs(product);
        assertThat(controller.getStockAlerts(StockStatus.OUT_OF_STOCK, 0, 10))
                .isSameAs(alerts);
    }

    @Test
    void stockAndReferenceControllersDelegateOperations() {
        StockService stockService = mock(StockService.class);
        ProductService productService = mock(ProductService.class);
        BarcodeService barcodeService = mock(BarcodeService.class);
        StockController stockController = new StockController(stockService);
        InventoryReferenceController referenceController =
                new InventoryReferenceController(productService, barcodeService);
        Jwt jwt = jwt();
        StockAdjustmentRequest adjustment = mock(StockAdjustmentRequest.class);
        ProductResponse product = mock(ProductResponse.class);
        UnitResponse unit = mock(UnitResponse.class);
        BarcodeResponse barcode = new BarcodeResponse("2000000000015");
        @SuppressWarnings("unchecked")
        InventoryPage<StockTransactionResponse> ledger = mock(InventoryPage.class);
        when(stockService.adjustStock("actor", "product", adjustment)).thenReturn(product);
        when(stockService.getStockLedger("product", 0, 25)).thenReturn(ledger);
        when(productService.listUnits()).thenReturn(List.of(unit));
        when(barcodeService.generateBarcode("actor")).thenReturn(barcode);

        assertThat(stockController.adjustStock(jwt, "product", adjustment)).isSameAs(product);
        assertThat(stockController.getStockLedger("product", 0, 25)).isSameAs(ledger);
        assertThat(referenceController.listUnits()).containsExactly(unit);
        assertThat(referenceController.generateBarcode(jwt)).isSameAs(barcode);
        verify(barcodeService).generateBarcode("actor");
    }

    private Jwt jwt() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("actor");
        return jwt;
    }
}
