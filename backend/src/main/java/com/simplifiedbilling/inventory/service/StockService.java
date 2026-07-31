package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductResponse;
import com.simplifiedbilling.inventory.dto.StockAdjustmentRequest;
import com.simplifiedbilling.inventory.dto.StockTransactionResponse;

public interface StockService {

    ProductResponse adjustStock(
            String actorUserId,
            String productId,
            StockAdjustmentRequest request);

    InventoryPage<StockTransactionResponse> getStockLedger(
            String productId,
            int page,
            int size);
}
