package com.simplifiedbilling.inventory.controller;

import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductResponse;
import com.simplifiedbilling.inventory.dto.StockAdjustmentRequest;
import com.simplifiedbilling.inventory.dto.StockTransactionResponse;
import com.simplifiedbilling.inventory.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/inventory/products/{productId}")
public class StockController {

    private static final String INVENTORY_READ =
            "hasAnyRole('OWNER', 'ADMIN', 'INVENTORY_MANAGER', 'VIEWER')";
    private static final String INVENTORY_WRITE =
            "hasAnyRole('OWNER', 'ADMIN', 'INVENTORY_MANAGER')";

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/stock-adjustments")
    @PreAuthorize(INVENTORY_WRITE)
    public ProductResponse adjustStock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String productId,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return stockService.adjustStock(jwt.getSubject(), productId, request);
    }

    @GetMapping("/stock-ledger")
    @PreAuthorize(INVENTORY_READ)
    public InventoryPage<StockTransactionResponse> getStockLedger(
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return stockService.getStockLedger(productId, page, size);
    }
}
