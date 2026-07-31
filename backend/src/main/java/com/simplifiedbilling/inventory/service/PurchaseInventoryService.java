package com.simplifiedbilling.inventory.service;

import java.util.List;

public interface PurchaseInventoryService {

    List<PurchaseProductSnapshot> receivePurchase(
            String actorUserId,
            String purchaseId,
            List<PurchaseStockRequest> items);
}
