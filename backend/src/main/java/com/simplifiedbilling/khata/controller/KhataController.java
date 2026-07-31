package com.simplifiedbilling.khata.controller;

import com.simplifiedbilling.khata.domain.BalanceStatus;
import com.simplifiedbilling.khata.dto.KhataPage;
import com.simplifiedbilling.khata.dto.KhataRequests;
import com.simplifiedbilling.khata.dto.KhataResponses;
import com.simplifiedbilling.khata.service.KhataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

@Validated
@RestController
@RequestMapping("/api/v1/khata")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'CASHIER')")
public class KhataController {

    private final KhataService khataService;

    public KhataController(KhataService khataService) {
        this.khataService = khataService;
    }

    @GetMapping("/summary")
    public KhataResponses.SummaryResponse summary() {
        return khataService.getSummary();
    }

    @GetMapping("/customers")
    public KhataPage<KhataResponses.CustomerResponse> searchCustomers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "ALL") BalanceStatus balanceStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return khataService.searchCustomers(query, active, balanceStatus, page, size);
    }

    @GetMapping("/customers/{customerId}")
    public KhataResponses.CustomerResponse getCustomer(@PathVariable String customerId) {
        return khataService.getCustomer(customerId);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public KhataResponses.CustomerResponse createCustomer(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody KhataRequests.CreateCustomerRequest request) {
        return khataService.createCustomer(jwt.getSubject(), request);
    }

    @PutMapping("/customers/{customerId}")
    public KhataResponses.CustomerResponse updateCustomer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String customerId,
            @Valid @RequestBody KhataRequests.UpdateCustomerRequest request) {
        return khataService.updateCustomer(jwt.getSubject(), customerId, request);
    }

    @GetMapping("/customers/{customerId}/statement")
    public KhataPage<KhataResponses.LedgerEntryResponse> statement(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return khataService.getStatement(customerId, page, size);
    }

    @PostMapping("/customers/{customerId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public KhataResponses.SettlementResponse settle(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String customerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody KhataRequests.SettlementRequest request) {
        return khataService.settle(jwt.getSubject(), customerId, idempotencyKey, request);
    }
}
