package com.simplifiedbilling.pos.controller;

import com.simplifiedbilling.pos.dto.InvoiceQueryResponses;
import com.simplifiedbilling.pos.service.InvoiceQueryService;
import com.simplifiedbilling.pos.service.InvoiceOutputType;
import com.simplifiedbilling.pos.service.InvoiceSearchCriteria;
import com.simplifiedbilling.pos.domain.InvoiceStatus;
import com.simplifiedbilling.pos.domain.PaymentMode;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class InvoiceQueryController {
    private final InvoiceQueryService service;

    public InvoiceQueryController(InvoiceQueryService service) { this.service = service; }

    @GetMapping
    public InvoiceQueryResponses.InvoicePage search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) PaymentMode paymentMode,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "NEWEST") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.search(new InvoiceSearchCriteria(
                query, status, paymentMode, from, to, minAmount, maxAmount, sort, page, size));
    }

    @GetMapping("/{invoiceId}/activity")
    public List<InvoiceQueryResponses.InvoiceActivity> activity(@PathVariable String invoiceId) {
        return service.activity(invoiceId);
    }

    @PostMapping("/{invoiceId}/outputs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordOutput(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String invoiceId,
            @RequestParam InvoiceOutputType type) {
        service.recordOutput(jwt.getSubject(), invoiceId, type);
    }
}
