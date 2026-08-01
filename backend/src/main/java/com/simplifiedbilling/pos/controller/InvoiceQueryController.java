package com.simplifiedbilling.pos.controller;

import com.simplifiedbilling.pos.dto.InvoiceQueryResponses;
import com.simplifiedbilling.pos.service.InvoiceQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class InvoiceQueryController {
    private final InvoiceQueryService service;

    public InvoiceQueryController(InvoiceQueryService service) { this.service = service; }

    @GetMapping
    public InvoiceQueryResponses.InvoicePage search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.search(query, page, size);
    }
}
