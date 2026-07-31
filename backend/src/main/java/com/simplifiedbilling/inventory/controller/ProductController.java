package com.simplifiedbilling.inventory.controller;

import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductAlertResponse;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductLookupResponse;
import com.simplifiedbilling.inventory.dto.ProductResponse;
import com.simplifiedbilling.inventory.dto.ProductUpdateRequest;
import com.simplifiedbilling.inventory.service.ProductSearch;
import com.simplifiedbilling.inventory.service.ProductService;
import com.simplifiedbilling.inventory.service.ProductSort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/inventory")
public class ProductController {

    private static final String INVENTORY_READ =
            "hasAnyRole('OWNER', 'ADMIN', 'INVENTORY_MANAGER', 'VIEWER')";
    private static final String INVENTORY_WRITE =
            "hasAnyRole('OWNER', 'ADMIN', 'INVENTORY_MANAGER')";

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    @PreAuthorize(INVENTORY_READ)
    public InventoryPage<ProductResponse> searchProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "ALL") StockStatus stockStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "NAME_ASC") ProductSort sort) {

        return productService.searchProducts(new ProductSearch(
                query,
                categoryId,
                active,
                stockStatus,
                page,
                size,
                sort));
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize(INVENTORY_READ)
    public ProductResponse getProduct(@PathVariable String productId) {
        return productService.getProduct(productId);
    }

    @GetMapping("/products/by-barcode/{barcode}")
    @PreAuthorize("isAuthenticated()")
    public ProductLookupResponse findByBarcode(@PathVariable @NotBlank String barcode) {
        return productService.findByBarcode(barcode);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(INVENTORY_WRITE)
    public ProductResponse createProduct(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProductCreateRequest request) {
        return productService.createProduct(jwt.getSubject(), request);
    }

    @PutMapping("/products/{productId}")
    @PreAuthorize(INVENTORY_WRITE)
    public ProductResponse updateProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        return productService.updateProduct(jwt.getSubject(), productId, request);
    }

    @GetMapping("/stock-alerts")
    @PreAuthorize(INVENTORY_READ)
    public InventoryPage<ProductAlertResponse> getStockAlerts(
            @RequestParam(defaultValue = "LOW_STOCK") StockStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return productService.getStockAlerts(status, page, size);
    }
}
