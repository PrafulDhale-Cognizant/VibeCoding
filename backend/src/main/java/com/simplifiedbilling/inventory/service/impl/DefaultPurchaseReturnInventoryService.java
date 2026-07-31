package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.InventoryBalance;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.repository.InventoryBalanceRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.inventory.service.PurchaseReturnInventoryService;
import com.simplifiedbilling.inventory.service.PurchaseReturnStockRequest;
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
public class DefaultPurchaseReturnInventoryService implements PurchaseReturnInventoryService {

    private final InventoryBalanceRepository balanceRepository;
    private final StockTransactionRepository transactionRepository;
    private final Clock clock;

    public DefaultPurchaseReturnInventoryService(
            InventoryBalanceRepository balanceRepository,
            StockTransactionRepository transactionRepository,
            Clock clock) {
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void returnToSupplier(
            String actorUserId,
            String purchaseReturnId,
            List<PurchaseReturnStockRequest> items) {
        if (items == null || items.isEmpty()) {
            throw invalid("EMPTY_PURCHASE_RETURN", "Select at least one purchase line to return.");
        }

        Map<String, PurchaseReturnStockRequest> normalized = new HashMap<>();
        for (PurchaseReturnStockRequest requested : items) {
            if (requested == null || requested.productId() == null || requested.productId().isBlank()) {
                throw productNotFound();
            }
            String productId = requested.productId().trim();
            BigDecimal quantity = quantity(requested.quantity());
            PurchaseReturnStockRequest item = new PurchaseReturnStockRequest(
                    productId, requested.productName(), quantity);
            if (normalized.put(productId, item) != null) {
                throw invalid(
                        "DUPLICATE_RETURN_PRODUCT",
                        "A product can appear only once in a purchase return.");
            }
        }

        List<String> productIds = normalized.keySet().stream().sorted().toList();
        List<InventoryBalance> balances = balanceRepository.findAllByProductIdsForUpdate(productIds);
        if (balances.size() != productIds.size()) {
            throw productNotFound();
        }

        Instant now = Instant.now(clock);
        for (InventoryBalance balance : balances) {
            Product product = balance.getProduct();
            PurchaseReturnStockRequest item = normalized.get(product.getId());
            validateUnitQuantity(item.quantity(), product);
            if (balance.getQuantity().compareTo(item.quantity()) < 0) {
                throw new ApplicationException(
                        HttpStatus.CONFLICT,
                        "INSUFFICIENT_RETURN_STOCK",
                        product.getName() + " has insufficient stock for this supplier return.");
            }
            BigDecimal delta = item.quantity().negate();
            BigDecimal balanceAfter = balance.adjust(delta, now);
            transactionRepository.save(StockTransaction.create(
                    product,
                    StockTransactionType.PURCHASE_RETURN,
                    delta,
                    balanceAfter,
                    StockReasonCode.PURCHASE_RETURN,
                    "PURCHASE_RETURN",
                    purchaseReturnId,
                    "Stock returned to supplier",
                    actorUserId,
                    now));
        }
        balanceRepository.flush();
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw invalid("INVALID_RETURN_QUANTITY", "Return quantity must be greater than zero.");
        }
        try {
            return value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid(
                    "INVALID_QUANTITY_PRECISION",
                    "Return quantities support at most three decimal places.");
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
                HttpStatus.NOT_FOUND,
                "PRODUCT_NOT_FOUND",
                "A purchase-return product does not exist.");
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, code, message);
    }
}
