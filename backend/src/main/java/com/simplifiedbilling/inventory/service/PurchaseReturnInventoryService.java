package com.simplifiedbilling.inventory.service;

import java.util.List;

public interface PurchaseReturnInventoryService {

    void returnToSupplier(
            String actorUserId,
            String purchaseReturnId,
            List<PurchaseReturnStockRequest> items);
}
