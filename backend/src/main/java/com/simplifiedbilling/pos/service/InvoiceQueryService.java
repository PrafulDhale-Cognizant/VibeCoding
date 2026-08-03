package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.pos.dto.InvoiceQueryResponses;

import java.util.List;

public interface InvoiceQueryService {
    InvoiceQueryResponses.InvoicePage search(InvoiceSearchCriteria criteria);

    default InvoiceQueryResponses.InvoicePage search(String query, int page, int size) {
        return search(new InvoiceSearchCriteria(
                query, null, null, null, null, null, null, "NEWEST", page, size));
    }

    List<InvoiceQueryResponses.InvoiceActivity> activity(String invoiceId);

    void recordOutput(String actorUserId, String invoiceId, InvoiceOutputType outputType);
}
