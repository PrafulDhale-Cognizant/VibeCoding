package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductAlertResponse;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductLookupResponse;
import com.simplifiedbilling.inventory.dto.ProductResponse;
import com.simplifiedbilling.inventory.dto.ProductUpdateRequest;
import com.simplifiedbilling.inventory.dto.UnitResponse;

import java.util.List;

public interface ProductService {

    InventoryPage<ProductResponse> searchProducts(ProductSearch search);

    ProductResponse getProduct(String productId);

    ProductLookupResponse findByBarcode(String barcode);

    ProductResponse createProduct(String actorUserId, ProductCreateRequest request);

    ProductResponse updateProduct(
            String actorUserId,
            String productId,
            ProductUpdateRequest request);

    InventoryPage<ProductAlertResponse> getStockAlerts(
            StockStatus status,
            int page,
            int size);

    List<UnitResponse> listUnits();
}
