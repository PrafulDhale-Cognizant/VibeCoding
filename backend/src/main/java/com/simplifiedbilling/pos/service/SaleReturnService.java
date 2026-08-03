package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.pos.dto.SaleReturnRequests;
import com.simplifiedbilling.pos.dto.SaleReturnResponses;

public interface SaleReturnService {
    SaleReturnResponses.SourceInvoice findSourceInvoice(String invoiceNumber);
    SaleReturnResponses.ReturnResponse returnItems(
            String actorUserId, String invoiceId, String idempotencyKey,
            SaleReturnRequests.CreateRequest request);
    SaleReturnResponses.ReturnResponse cancel(
            String actorUserId, String invoiceId, String idempotencyKey,
            SaleReturnRequests.CancellationRequest request);
    SaleReturnResponses.ReturnResponse getReturn(String saleReturnId);
    InventoryPage<SaleReturnResponses.ReturnSummary> searchReturns(String query, int page, int size);
}
