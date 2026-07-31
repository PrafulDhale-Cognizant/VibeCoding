package com.simplifiedbilling.inventory.mapper;

import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.ProductData;
import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.dto.ProductAlertResponse;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductLookupResponse;
import com.simplifiedbilling.inventory.dto.ProductResponse;
import com.simplifiedbilling.inventory.dto.ProductUpdateRequest;
import com.simplifiedbilling.inventory.dto.StockTransactionResponse;
import com.simplifiedbilling.inventory.dto.UnitResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ProductMapper {

    private final CategoryMapper categoryMapper;

    public ProductMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public ProductData toData(ProductCreateRequest request) {
        return new ProductData(
                request.name().trim(),
                receiptName(request.name(), request.receiptName()),
                upperOrNull(request.sku()),
                request.unit(),
                upperOrNull(request.hsnCode()),
                money(request.gstRate()),
                money(request.purchaseCost()),
                money(request.sellingPrice()),
                quantity(request.minimumStockLevel()),
                true);
    }

    public ProductData toData(ProductUpdateRequest request) {
        return new ProductData(
                request.name().trim(),
                receiptName(request.name(), request.receiptName()),
                upperOrNull(request.sku()),
                request.unit(),
                upperOrNull(request.hsnCode()),
                money(request.gstRate()),
                money(request.purchaseCost()),
                money(request.sellingPrice()),
                quantity(request.minimumStockLevel()),
                request.active());
    }

    public ProductResponse toResponse(Product product) {
        BigDecimal stock = product.getStockBalance().getQuantity();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getReceiptName(),
                product.getSku(),
                product.getBarcode().getValue(),
                product.getBarcode().isInternal(),
                categoryMapper.toResponse(product.getCategory()),
                product.getUnit(),
                product.getHsnCode(),
                product.getGstRate(),
                product.getPurchaseCost(),
                product.getSellingPrice(),
                stock,
                product.getMinimumStockLevel(),
                stockStatus(stock, product.getMinimumStockLevel()),
                product.isActive(),
                product.getVersion(),
                product.getStockBalance().getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public ProductLookupResponse toLookupResponse(Product product) {
        return new ProductLookupResponse(
                product.getId(),
                product.getName(),
                product.getReceiptName(),
                product.getBarcode().getValue(),
                product.getUnit(),
                product.getGstRate(),
                product.getSellingPrice(),
                product.getStockBalance().getQuantity(),
                product.isActive());
    }

    public ProductAlertResponse toAlertResponse(Product product) {
        BigDecimal stock = product.getStockBalance().getQuantity();
        BigDecimal reorder = product.getMinimumStockLevel().subtract(stock).max(BigDecimal.ZERO);
        return new ProductAlertResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getUnit(),
                stock,
                product.getMinimumStockLevel(),
                reorder,
                stockStatus(stock, product.getMinimumStockLevel()));
    }

    public StockTransactionResponse toTransactionResponse(StockTransaction transaction) {
        return new StockTransactionResponse(
                transaction.getId(),
                transaction.getProduct().getId(),
                transaction.getTransactionType(),
                transaction.getQuantityDelta(),
                transaction.getBalanceAfter(),
                transaction.getReasonCode(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getNotes(),
                transaction.getActorUserId(),
                transaction.getOccurredAt());
    }

    public UnitResponse toUnitResponse(ProductUnit unit) {
        return new UnitResponse(
                unit,
                unit.getDisplayName(),
                unit.getSymbol(),
                unit.isDecimalAllowed());
    }

    public String normalizeBarcode(String value) {
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public String normalizeSku(String value) {
        return upperOrNull(value);
    }

    public BigDecimal quantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.UNNECESSARY);
    }

    public StockStatus stockStatus(BigDecimal quantity, BigDecimal minimum) {
        if (quantity.signum() == 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        if (quantity.compareTo(minimum) <= 0) {
            return StockStatus.LOW_STOCK;
        }
        return StockStatus.IN_STOCK;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String receiptName(String name, String receiptName) {
        if (receiptName != null && !receiptName.isBlank()) {
            return receiptName.trim();
        }
        String normalizedName = name.trim();
        return normalizedName.length() <= 80
                ? normalizedName
                : normalizedName.substring(0, 80);
    }

    private String upperOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
