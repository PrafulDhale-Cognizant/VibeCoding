package com.simplifiedbilling.pos.controller;

import com.simplifiedbilling.pos.dto.SaleReturnRequests;
import com.simplifiedbilling.pos.dto.SaleReturnResponses;
import com.simplifiedbilling.pos.service.SaleReturnService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class SaleReturnController {
    private final SaleReturnService service;

    public SaleReturnController(SaleReturnService service) { this.service = service; }

    @GetMapping("/return-source")
    public SaleReturnResponses.SourceInvoice source(@RequestParam String invoiceNumber) {
        return service.findSourceInvoice(invoiceNumber);
    }

    @PostMapping("/invoices/{invoiceId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleReturnResponses.ReturnResponse returnItems(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String invoiceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SaleReturnRequests.CreateRequest request) {
        return service.returnItems(jwt.getSubject(), invoiceId, idempotencyKey, request);
    }

    @PostMapping("/invoices/{invoiceId}/cancel")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleReturnResponses.ReturnResponse cancel(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String invoiceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SaleReturnRequests.CancellationRequest request) {
        return service.cancel(jwt.getSubject(), invoiceId, idempotencyKey, request);
    }

    @GetMapping("/returns/{saleReturnId}")
    public SaleReturnResponses.ReturnResponse get(@PathVariable String saleReturnId) {
        return service.getReturn(saleReturnId);
    }
}
