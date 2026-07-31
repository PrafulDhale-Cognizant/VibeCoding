package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.domain.Product;
import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockReasonCode;
import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.domain.StockTransaction;
import com.simplifiedbilling.inventory.domain.StockTransactionType;
import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductAlertResponse;
import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import com.simplifiedbilling.inventory.dto.ProductLookupResponse;
import com.simplifiedbilling.inventory.dto.ProductResponse;
import com.simplifiedbilling.inventory.dto.ProductUpdateRequest;
import com.simplifiedbilling.inventory.dto.UnitResponse;
import com.simplifiedbilling.inventory.mapper.ProductMapper;
import com.simplifiedbilling.inventory.repository.CategoryRepository;
import com.simplifiedbilling.inventory.repository.ProductBarcodeRepository;
import com.simplifiedbilling.inventory.repository.ProductRepository;
import com.simplifiedbilling.inventory.repository.StockTransactionRepository;
import com.simplifiedbilling.inventory.service.InternalBarcodeAllocator;
import com.simplifiedbilling.inventory.service.ProductSearch;
import com.simplifiedbilling.inventory.service.ProductService;
import com.simplifiedbilling.inventory.service.ProductSort;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultProductService implements ProductService {

    private final ProductRepository productRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final CategoryRepository categoryRepository;
    private final StockTransactionRepository transactionRepository;
    private final InternalBarcodeAllocator barcodeAllocator;
    private final ProductMapper productMapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public DefaultProductService(
            ProductRepository productRepository,
            ProductBarcodeRepository barcodeRepository,
            CategoryRepository categoryRepository,
            StockTransactionRepository transactionRepository,
            InternalBarcodeAllocator barcodeAllocator,
            ProductMapper productMapper,
            AuditWriter auditWriter,
            Clock clock) {
        this.productRepository = productRepository;
        this.barcodeRepository = barcodeRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.barcodeAllocator = barcodeAllocator;
        this.productMapper = productMapper;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryPage<ProductResponse> searchProducts(ProductSearch search) {
        Pageable pageable = pageRequest(search.page(), search.size(), search.sort());
        String searchPattern = normalizeSearch(search.query());
        StockStatus stockStatus = search.stockStatus() == null ? StockStatus.ALL : search.stockStatus();
        Page<Product> products = productRepository.search(
                searchPattern,
                blankToNull(search.categoryId()),
                search.active(),
                stockStatus.name(),
                pageable);
        return InventoryPage.from(products, productMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(String productId) {
        return productMapper.toResponse(requireProduct(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductLookupResponse findByBarcode(String barcode) {
        String normalized = productMapper.normalizeBarcode(barcode);
        Product product = productRepository.findDetailedByBarcode(normalized)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "BARCODE_NOT_FOUND",
                        "No product matches this barcode."));
        return productMapper.toLookupResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(String actorUserId, ProductCreateRequest request) {
        Category category = requireActiveCategory(request.categoryId());
        String sku = productMapper.normalizeSku(request.sku());
        ensureSkuAvailable(sku, null);

        boolean internalBarcode = request.generateBarcode();
        String barcode = internalBarcode
                ? barcodeAllocator.allocate()
                : productMapper.normalizeBarcode(request.barcode());
        ensureBarcodeAvailable(barcode, null);

        BigDecimal openingStock = normalizeQuantity(request.openingStock(), request.unit());
        normalizeQuantity(request.minimumStockLevel(), request.unit());
        Instant now = Instant.now(clock);
        Product product = Product.create(
                productMapper.toData(request),
                category,
                barcode,
                internalBarcode,
                openingStock,
                now);
        productRepository.saveAndFlush(product);

        if (openingStock.signum() != 0) {
            transactionRepository.save(StockTransaction.create(
                    product,
                    StockTransactionType.OPENING,
                    openingStock,
                    openingStock,
                    StockReasonCode.OPENING_STOCK,
                    "PRODUCT",
                    product.getId(),
                    "Opening stock",
                    actorUserId,
                    now));
        }

        auditWriter.write(
                actorUserId,
                "PRODUCT_CREATED",
                "PRODUCT",
                product.getId(),
                Map.of("name", product.getName(), "barcode", barcode));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(
            String actorUserId,
            String productId,
            ProductUpdateRequest request) {

        Product product = requireProduct(productId);
        if (product.getVersion() != request.version()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "STALE_PRODUCT_VERSION",
                    "The product has changed. Refresh and try again.");
        }
        Category category = requireActiveCategory(request.categoryId());
        String sku = productMapper.normalizeSku(request.sku());
        String barcode = productMapper.normalizeBarcode(request.barcode());
        ensureSkuAvailable(sku, productId);
        ensureBarcodeAvailable(barcode, productId);
        normalizeQuantity(request.minimumStockLevel(), request.unit());

        boolean remainsInternal = product.getBarcode().isInternal()
                && product.getBarcode().getValue().equals(barcode);
        product.update(
                productMapper.toData(request),
                category,
                barcode,
                remainsInternal,
                Instant.now(clock));
        productRepository.flush();
        auditWriter.write(
                actorUserId,
                "PRODUCT_UPDATED",
                "PRODUCT",
                product.getId(),
                Map.of("name", product.getName(), "active", product.isActive()));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryPage<ProductAlertResponse> getStockAlerts(
            StockStatus status,
            int page,
            int size) {

        if (status != StockStatus.LOW_STOCK && status != StockStatus.OUT_OF_STOCK) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STOCK_ALERT_STATUS",
                    "Stock alerts support LOW_STOCK or OUT_OF_STOCK.");
        }
        Page<Product> products = productRepository.search(
                null,
                null,
                true,
                status.name(),
                pageRequest(page, size, ProductSort.STOCK_ASC));
        return InventoryPage.from(products, productMapper::toAlertResponse);
    }

    @Override
    public List<UnitResponse> listUnits() {
        return Arrays.stream(ProductUnit.values())
                .map(productMapper::toUnitResponse)
                .toList();
    }

    private Product requireProduct(String id) {
        return productRepository.findDetailedById(id)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND",
                        "The product does not exist."));
    }

    private Category requireActiveCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "CATEGORY_NOT_FOUND",
                        "The category does not exist."));
        if (!category.isActive()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "CATEGORY_INACTIVE",
                    "Select an active category.");
        }
        return category;
    }

    private void ensureSkuAvailable(String sku, String productId) {
        if (sku == null) {
            return;
        }
        boolean exists = productId == null
                ? productRepository.existsBySkuIgnoreCase(sku)
                : productRepository.existsBySkuIgnoreCaseAndIdNot(sku, productId);
        if (exists) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "SKU_EXISTS",
                    "Another product already uses this SKU.");
        }
    }

    private void ensureBarcodeAvailable(String barcode, String productId) {
        boolean exists = productId == null
                ? barcodeRepository.existsByValue(barcode)
                : barcodeRepository.existsByValueAndProduct_IdNot(barcode, productId);
        if (exists) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "BARCODE_EXISTS",
                    "Another product already uses this barcode.");
        }
    }

    private BigDecimal normalizeQuantity(BigDecimal value, ProductUnit unit) {
        BigDecimal normalized;
        try {
            normalized = productMapper.quantity(value);
        } catch (ArithmeticException exception) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY_PRECISION",
                    "Quantities support at most three decimal places.");
        }
        if (!unit.isDecimalAllowed() && normalized.stripTrailingZeros().scale() > 0) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "FRACTIONAL_QUANTITY_NOT_ALLOWED",
                    "The selected unit requires a whole-number quantity.");
        }
        return normalized;
    }

    private Pageable pageRequest(int page, int size, ProductSort requestedSort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_REQUEST",
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        ProductSort sort = requestedSort == null ? ProductSort.NAME_ASC : requestedSort;
        Sort springSort = switch (sort) {
            case NAME_ASC -> Sort.by("name").ascending();
            case NAME_DESC -> Sort.by("name").descending();
            case UPDATED_DESC -> Sort.by("updatedAt").descending();
            case PRICE_ASC -> Sort.by("sellingPrice").ascending();
            case STOCK_ASC -> Sort.by("stockBalance.quantity").ascending();
        };
        return PageRequest.of(page, size, springSort);
    }

    private String normalizeSearch(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
