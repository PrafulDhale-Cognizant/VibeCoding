package com.simplifiedbilling.pos.controller;

import com.simplifiedbilling.pos.dto.PosRequests;
import com.simplifiedbilling.pos.dto.PosResponses;
import com.simplifiedbilling.pos.service.PosService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'CASHIER')")
public class PosController {

    private final PosService posService;

    public PosController(PosService posService) {
        this.posService = posService;
    }

    @PostMapping("/quote")
    public PosResponses.QuoteResponse quote(@Valid @RequestBody PosRequests.QuoteRequest request) {
        return posService.quote(request);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public PosResponses.InvoiceResponse checkout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PosRequests.CheckoutRequest request) {
        return posService.checkout(jwt.getSubject(), idempotencyKey, request);
    }

    @GetMapping("/invoices/{invoiceId}")
    public PosResponses.InvoiceResponse getInvoice(@PathVariable String invoiceId) {
        return posService.getInvoice(invoiceId);
    }
}
