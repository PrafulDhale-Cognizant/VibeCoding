package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.pos.dto.PosRequests;
import com.simplifiedbilling.pos.dto.PosResponses;

public interface PosService {

    PosResponses.QuoteResponse quote(PosRequests.QuoteRequest request);

    PosResponses.InvoiceResponse checkout(
            String actorUserId,
            String idempotencyKey,
            PosRequests.CheckoutRequest request);

    PosResponses.InvoiceResponse getInvoice(String invoiceId);
}
