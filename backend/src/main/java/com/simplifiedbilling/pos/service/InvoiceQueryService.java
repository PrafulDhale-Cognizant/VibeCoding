package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.pos.dto.InvoiceQueryResponses;

public interface InvoiceQueryService {
    InvoiceQueryResponses.InvoicePage search(String query, int page, int size);
}
