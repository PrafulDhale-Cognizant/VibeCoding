package com.simplifiedbilling.inventory.service;

import java.util.Collection;
import java.util.List;

/** Module boundary used by checkout; POS never accesses inventory repositories directly. */
public interface CheckoutInventoryService {

    List<SaleProductSnapshot> getSaleProducts(Collection<String> productIds);

    List<SaleProductSnapshot> deductForSale(
            String actorUserId,
            String invoiceId,
            List<SaleStockRequest> items);

    void restoreSaleableReturns(
            String actorUserId,
            String saleReturnId,
            List<SaleReturnStockRequest> items);
}
