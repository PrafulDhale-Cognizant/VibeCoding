package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.InventoryBalance;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.repository.InventoryBalanceRepository;
import com.simplifiedbilling.inventory.repository.ProductRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.inventory.service.CheckoutInventoryService;
import com.simplifiedbilling.inventory.service.SaleProductSnapshot;
import com.simplifiedbilling.inventory.service.SaleStockRequest;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class DefaultCheckoutInventoryService implements CheckoutInventoryService {

    private final ProductRepository productRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final StockTransactionRepository transactionRepository;
    private final Clock clock;

    public DefaultCheckoutInventoryService(
            ProductRepository productRepository,
            InventoryBalanceRepository balanceRepository,
            StockTransactionRepository transactionRepository,
            Clock clock) {
        this.productRepository = productRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleProductSnapshot> getSaleProducts(Collection<String> productIds) {
        List<String> ids = distinctIds(productIds);
        Map<String, Product> products = new HashMap<>();
        productRepository.findDetailedByIdIn(ids).forEach(product -> products.put(product.getId(), product));
        return ids.stream().map(id -> {
            Product product = products.get(id);
            if (product == null) {
                throw productNotFound();
            }
            validateActive(product);
            return snapshot(product, product.getStockBalance().getQuantity());
        }).toList();
    }

    @Override
    @Transactional
    public List<SaleProductSnapshot> deductForSale(
            String actorUserId,
            String invoiceId,
            List<SaleStockRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "EMPTY_CART", "Add at least one item.");
        }

        Map<String, BigDecimal> quantityByProduct = new HashMap<>();
        for (SaleStockRequest item : items) {
            if (item == null || item.productId() == null || item.productId().isBlank()) {
                throw productNotFound();
            }
            if (quantityByProduct.put(item.productId(), normalizeQuantity(item.quantity())) != null) {
                throw new ApplicationException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_CART_PRODUCT",
                        "A product can appear only once in the cart.");
            }
        }

        List<String> sortedIds = quantityByProduct.keySet().stream().sorted().toList();
        List<InventoryBalance> balances = balanceRepository.findAllByProductIdsForUpdate(sortedIds);
        if (balances.size() != sortedIds.size()) {
            throw productNotFound();
        }

        Instant now = Instant.now(clock);
        Map<String, SaleProductSnapshot> results = new HashMap<>();
        for (InventoryBalance balance : balances) {
            Product product = balance.getProduct();
            validateActive(product);
            BigDecimal quantity = quantityByProduct.get(product.getId());
            validateUnitQuantity(quantity, product);
            if (balance.getQuantity().compareTo(quantity) < 0) {
                throw new ApplicationException(
                        HttpStatus.CONFLICT,
                        "INSUFFICIENT_STOCK",
                        product.getName() + " has only " + balance.getQuantity().stripTrailingZeros().toPlainString()
                                + " available.");
            }
            BigDecimal availableBeforeSale = balance.getQuantity();
            BigDecimal balanceAfter = balance.adjust(quantity.negate(), now);
            transactionRepository.save(StockTransaction.create(
                    product,
                    StockTransactionType.SALE,
                    quantity.negate(),
                    balanceAfter,
                    StockReasonCode.SALE,
                    "INVOICE",
                    invoiceId,
                    "Stock deducted at checkout",
                    actorUserId,
                    now));
            // Pricing validates against the quantity locked for this checkout, not the post-sale balance.
            results.put(product.getId(), snapshot(product, availableBeforeSale));
        }
        balanceRepository.flush();
        return items.stream().map(item -> results.get(item.productId())).toList();
    }

    private List<String> distinctIds(Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "EMPTY_CART", "Add at least one item.");
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String id : productIds) {
            if (id == null || id.isBlank()) {
                throw productNotFound();
            }
            if (!ids.add(id)) {
                throw new ApplicationException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_CART_PRODUCT",
                        "A product can appear only once in the cart.");
            }
        }
        return List.copyOf(ids);
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Quantity must be greater than zero.");
        }
        try {
            return quantity.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY_PRECISION",
                    "Quantities support at most three decimal places.");
        }
    }

    private void validateUnitQuantity(BigDecimal quantity, Product product) {
        if (!product.getUnit().isDecimalAllowed() && quantity.stripTrailingZeros().scale() > 0) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "FRACTIONAL_QUANTITY_NOT_ALLOWED",
                    product.getName() + " requires a whole-number quantity.");
        }
    }

    private void validateActive(Product product) {
        if (!product.isActive()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "INACTIVE_PRODUCT",
                    product.getName() + " is inactive and cannot be sold.");
        }
    }

    private SaleProductSnapshot snapshot(Product product, BigDecimal availableQuantity) {
        return new SaleProductSnapshot(
                product.getId(),
                product.getName(),
                product.getReceiptName(),
                product.getBarcode().getValue(),
                product.getUnit(),
                product.getGstRate(),
                product.getPurchaseCost(),
                product.getSellingPrice(),
                availableQuantity);
    }

    private ApplicationException productNotFound() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "A cart product does not exist.");
    }
}
