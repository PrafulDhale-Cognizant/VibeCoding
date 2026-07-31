package com.simplifiedbilling.purchasing.controller;

import com.simplifiedbilling.purchasing.domain.SupplierBalanceStatus;
import com.simplifiedbilling.purchasing.dto.PurchasingPage;
import com.simplifiedbilling.purchasing.dto.PurchasingRequests;
import com.simplifiedbilling.purchasing.dto.PurchasingResponses;
import com.simplifiedbilling.purchasing.service.PurchasingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/v1/purchasing")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INVENTORY_MANAGER')")
public class PurchasingController {

    private final PurchasingService purchasingService;

    public PurchasingController(PurchasingService purchasingService) {
        this.purchasingService = purchasingService;
    }

    @GetMapping("/summary")
    public PurchasingResponses.SummaryResponse summary() {
        return purchasingService.getSummary();
    }

    @GetMapping("/suppliers")
    public PurchasingPage<PurchasingResponses.SupplierResponse> suppliers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "ALL") SupplierBalanceStatus balanceStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return purchasingService.searchSuppliers(query, active, balanceStatus, page, size);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchasingResponses.SupplierResponse createSupplier(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PurchasingRequests.CreateSupplierRequest request) {
        return purchasingService.createSupplier(jwt.getSubject(), request);
    }

    @GetMapping("/suppliers/{supplierId}")
    public PurchasingResponses.SupplierResponse getSupplier(@PathVariable String supplierId) {
        return purchasingService.getSupplier(supplierId);
    }

    @PutMapping("/suppliers/{supplierId}")
    public PurchasingResponses.SupplierResponse updateSupplier(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String supplierId,
            @Valid @RequestBody PurchasingRequests.UpdateSupplierRequest request) {
        return purchasingService.updateSupplier(jwt.getSubject(), supplierId, request);
    }

    @GetMapping("/suppliers/{supplierId}/statement")
    public PurchasingPage<PurchasingResponses.SupplierLedgerResponse> statement(
            @PathVariable String supplierId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return purchasingService.getSupplierStatement(supplierId, page, size);
    }

    @PostMapping("/suppliers/{supplierId}/payments")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchasingResponses.SupplierPaymentResponse paySupplier(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String supplierId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PurchasingRequests.SupplierPaymentRequest request) {
        return purchasingService.paySupplier(
                jwt.getSubject(), supplierId, idempotencyKey, request);
    }

    @GetMapping("/purchases")
    public PurchasingPage<PurchasingResponses.PurchaseSummaryResponse> purchases(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return purchasingService.searchPurchases(query, supplierId, from, to, page, size);
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchasingResponses.PurchaseResponse receivePurchase(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PurchasingRequests.ReceivePurchaseRequest request) {
        return purchasingService.receivePurchase(jwt.getSubject(), idempotencyKey, request);
    }

    @GetMapping("/purchases/{purchaseId}")
    public PurchasingResponses.PurchaseResponse getPurchase(@PathVariable String purchaseId) {
        return purchasingService.getPurchase(purchaseId);
    }
}
