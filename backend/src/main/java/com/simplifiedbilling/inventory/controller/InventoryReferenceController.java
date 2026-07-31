package com.simplifiedbilling.inventory.controller;

import com.simplifiedbilling.inventory.dto.BarcodeResponse;
import com.simplifiedbilling.inventory.dto.UnitResponse;
import com.simplifiedbilling.inventory.service.BarcodeService;
import com.simplifiedbilling.inventory.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryReferenceController {

    private final ProductService productService;
    private final BarcodeService barcodeService;

    public InventoryReferenceController(
            ProductService productService,
            BarcodeService barcodeService) {
        this.productService = productService;
        this.barcodeService = barcodeService;
    }

    @GetMapping("/units")
    @PreAuthorize("isAuthenticated()")
    public List<UnitResponse> listUnits() {
        return productService.listUnits();
    }

    @PostMapping("/barcodes/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INVENTORY_MANAGER')")
    public BarcodeResponse generateBarcode(@AuthenticationPrincipal Jwt jwt) {
        return barcodeService.generateBarcode(jwt.getSubject());
    }
}
