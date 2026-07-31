package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.InventoryBalance;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.repository.InventoryBalanceRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.inventory.service.PurchaseInventoryService;
import com.simplifiedbilling.inventory.service.PurchaseProductSnapshot;
import com.simplifiedbilling.inventory.service.PurchaseStockRequest;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultPurchaseInventoryService implements PurchaseInventoryService {

    private final InventoryBalanceRepository balanceRepository;
    private final StockTransactionRepository transactionRepository;
    private final Clock clock;

    public DefaultPurchaseInventoryService(
            InventoryBalanceRepository balanceRepository,
            StockTransactionRepository transactionRepository,
            Clock clock) {
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<PurchaseProductSnapshot> receivePurchase(
            String actorUserId,
            String purchaseId,
            List<PurchaseStockRequest> items) {
        if (items == null || items.isEmpty()) {
            throw invalid("EMPTY_PURCHASE", "Add at least one product to the purchase.");
        }

        Map<String, PurchaseStockRequest> normalized = new HashMap<>();
        for (PurchaseStockRequest requested : items) {
            if (requested == null || requested.productId() == null || requested.productId().isBlank()) {
                throw productNotFound();
            }
            PurchaseStockRequest item = new PurchaseStockRequest(
                    requested.productId().trim(), quantity(requested.quantity()), money(requested.unitCost()));
            if (item.unitCost().signum() <= 0) {
                throw invalid("INVALID_PURCHASE_COST", "Purchase unit cost must be greater than zero.");
            }
            if (normalized.put(item.productId(), item) != null) {
                throw invalid("DUPLICATE_PURCHASE_PRODUCT", "A product can appear only once in a purchase.");
            }
        }

        List<String> productIds = normalized.keySet().stream().sorted().toList();
        List<InventoryBalance> balances = balanceRepository.findAllByProductIdsForUpdate(productIds);
        if (balances.size() != productIds.size()) {
            throw productNotFound();
        }

        Instant now = Instant.now(clock);
        Map<String, PurchaseProductSnapshot> snapshots = new HashMap<>();
        for (InventoryBalance balance : balances) {
            Product product = balance.getProduct();
            PurchaseStockRequest item = normalized.get(product.getId());
            if (!product.isActive()) {
                throw new ApplicationException(
                        HttpStatus.CONFLICT,
                        "INACTIVE_PRODUCT",
                        product.getName() + " is inactive and cannot be received.");
            }
            validateUnitQuantity(item.quantity(), product);
            BigDecimal balanceAfter = balance.adjust(item.quantity(), now);
            product.updatePurchaseCost(item.unitCost(), now);
            transactionRepository.save(StockTransaction.create(
                    product,
                    StockTransactionType.PURCHASE,
                    item.quantity(),
                    balanceAfter,
                    StockReasonCode.PURCHASE,
                    "PURCHASE",
                    purchaseId,
                    "Stock received from supplier",
                    actorUserId,
                    now));
            snapshots.put(product.getId(), new PurchaseProductSnapshot(
                    product.getId(), product.getName(), product.getUnit(), item.quantity(),
                    item.unitCost(), product.getGstRate()));
        }
        balanceRepository.flush();
        return items.stream().map(item -> snapshots.get(item.productId().trim())).toList();
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw invalid("INVALID_QUANTITY", "Purchase quantity must be greater than zero.");
        }
        try {
            return value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("INVALID_QUANTITY_PRECISION", "Quantities support at most three decimal places.");
        }
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw invalid("INVALID_PURCHASE_COST", "Purchase unit cost is required.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("INVALID_MONEY_PRECISION", "Purchase costs support at most two decimal places.");
        }
    }

    private void validateUnitQuantity(BigDecimal value, Product product) {
        if (!product.getUnit().isDecimalAllowed() && value.stripTrailingZeros().scale() > 0) {
            throw invalid(
                    "FRACTIONAL_QUANTITY_NOT_ALLOWED",
                    product.getName() + " requires a whole-number quantity.");
        }
    }

    private ApplicationException productNotFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "A purchase product does not exist.");
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, code, message);
    }
}
