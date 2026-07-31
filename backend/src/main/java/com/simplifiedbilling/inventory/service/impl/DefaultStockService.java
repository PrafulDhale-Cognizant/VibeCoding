package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.InventoryBalance;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductResponse;
import com.simplifiedbilling.inventory.dto.StockAdjustmentRequest;
import com.simplifiedbilling.inventory.dto.StockTransactionResponse;
import com.simplifiedbilling.inventory.mapper.ProductMapper;
import com.simplifiedbilling.inventory.repository.InventoryBalanceRepository;
import com.simplifiedbilling.inventory.repository.ProductRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.inventory.service.StockService;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class DefaultStockService implements StockService {

    private final InventoryBalanceRepository balanceRepository;
    private final ProductRepository productRepository;
    private final StockTransactionRepository transactionRepository;
    private final ProductMapper productMapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public DefaultStockService(
            InventoryBalanceRepository balanceRepository,
            ProductRepository productRepository,
            StockTransactionRepository transactionRepository,
            ProductMapper productMapper,
            AuditWriter auditWriter,
            Clock clock) {
        this.balanceRepository = balanceRepository;
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
        this.productMapper = productMapper;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProductResponse adjustStock(
            String actorUserId,
            String productId,
            StockAdjustmentRequest request) {

        if (request.reasonCode() == StockReasonCode.OPENING_STOCK) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ADJUSTMENT_REASON",
                    "OPENING_STOCK can only be used while creating a product.");
        }
        BigDecimal delta = normalizeDelta(request.quantityDelta());
        if (delta.signum() == 0) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "ZERO_STOCK_ADJUSTMENT",
                    "Stock adjustment quantity cannot be zero.");
        }

        InventoryBalance balance = balanceRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND",
                        "The product does not exist."));
        if (balance.getVersion() != request.stockVersion()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "STALE_STOCK_VERSION",
                    "Stock has changed. Refresh and try again.");
        }
        Product product = balance.getProduct();
        validateUnitQuantity(delta, product);
        if (balance.getQuantity().add(delta).signum() < 0) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "The adjustment would make stock negative.");
        }

        Instant now = Instant.now(clock);
        BigDecimal balanceAfter = balance.adjust(delta, now);
        transactionRepository.save(StockTransaction.create(
                product,
                StockTransactionType.ADJUSTMENT,
                delta,
                balanceAfter,
                request.reasonCode(),
                null,
                null,
                normalizeNotes(request.notes()),
                actorUserId,
                now));
        balanceRepository.flush();
        auditWriter.write(
                actorUserId,
                "STOCK_ADJUSTED",
                "PRODUCT",
                productId,
                Map.of("quantityDelta", delta, "reason", request.reasonCode().name()));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryPage<StockTransactionResponse> getStockLedger(
            String productId,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_REQUEST",
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        if (!productRepository.existsById(productId)) {
            throw new ApplicationException(
                    HttpStatus.NOT_FOUND,
                    "PRODUCT_NOT_FOUND",
                    "The product does not exist.");
        }
        Page<StockTransaction> transactions = transactionRepository
                .findByProduct_IdOrderByOccurredAtDesc(productId, PageRequest.of(page, size));
        return InventoryPage.from(transactions, productMapper::toTransactionResponse);
    }

    private BigDecimal normalizeDelta(BigDecimal value) {
        try {
            return productMapper.quantity(value);
        } catch (ArithmeticException exception) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY_PRECISION",
                    "Quantities support at most three decimal places.");
        }
    }

    private void validateUnitQuantity(BigDecimal delta, Product product) {
        if (!product.getUnit().isDecimalAllowed() && delta.stripTrailingZeros().scale() > 0) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "FRACTIONAL_QUANTITY_NOT_ALLOWED",
                    "The product unit requires a whole-number quantity.");
        }
    }

    private String normalizeNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }
}
