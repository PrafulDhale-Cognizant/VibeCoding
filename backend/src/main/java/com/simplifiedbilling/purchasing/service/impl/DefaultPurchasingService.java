package com.simplifiedbilling.purchasing.service.impl;

import com.simplifiedbilling.inventory.service.PurchaseInventoryService;
import com.simplifiedbilling.inventory.service.PurchaseProductSnapshot;
import com.simplifiedbilling.inventory.service.PurchaseReturnInventoryService;
import com.simplifiedbilling.inventory.service.PurchaseReturnStockRequest;
import com.simplifiedbilling.inventory.service.PurchaseStockRequest;
import com.simplifiedbilling.purchasing.domain.Purchase;
import com.simplifiedbilling.purchasing.domain.PurchaseItem;
import com.simplifiedbilling.purchasing.domain.PurchaseReturn;
import com.simplifiedbilling.purchasing.domain.Supplier;
import com.simplifiedbilling.purchasing.domain.SupplierBalanceStatus;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntry;
import com.simplifiedbilling.purchasing.domain.SupplierPayableBalance;
import com.simplifiedbilling.purchasing.dto.PurchasingPage;
import com.simplifiedbilling.purchasing.dto.PurchasingRequests;
import com.simplifiedbilling.purchasing.dto.PurchasingResponses;
import com.simplifiedbilling.purchasing.mapper.PurchasingMapper;
import com.simplifiedbilling.purchasing.repository.PurchaseRepository;
import com.simplifiedbilling.purchasing.repository.PurchaseReturnRepository;
import com.simplifiedbilling.purchasing.repository.SupplierAmountAggregate;
import com.simplifiedbilling.purchasing.repository.SupplierLedgerRepository;
import com.simplifiedbilling.purchasing.repository.SupplierPayableBalanceRepository;
import com.simplifiedbilling.purchasing.repository.SupplierRepository;
import com.simplifiedbilling.purchasing.service.PurchaseNumberAllocator;
import com.simplifiedbilling.purchasing.service.PurchasePricingEngine;
import com.simplifiedbilling.purchasing.service.PurchaseReturnNumberAllocator;
import com.simplifiedbilling.purchasing.service.PurchaseReturnSelection;
import com.simplifiedbilling.purchasing.service.PurchasingService;
import com.simplifiedbilling.purchasing.service.SupplierPhoneNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.service.StoreService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DefaultPurchasingService implements PurchasingService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,80}");
    private static final Pattern GSTIN = Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]");

    private final SupplierRepository supplierRepository;
    private final SupplierPayableBalanceRepository balanceRepository;
    private final SupplierLedgerRepository ledgerRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseInventoryService inventoryService;
    private final PurchaseReturnInventoryService returnInventoryService;
    private final PurchasePricingEngine pricingEngine;
    private final PurchaseNumberAllocator numberAllocator;
    private final PurchaseReturnNumberAllocator returnNumberAllocator;
    private final SupplierPhoneNormalizer phoneNormalizer;
    private final PurchasingMapper mapper;
    private final AuditWriter auditWriter;
    private final StoreService storeService;
    private final Clock clock;

    public DefaultPurchasingService(
            SupplierRepository supplierRepository,
            SupplierPayableBalanceRepository balanceRepository,
            SupplierLedgerRepository ledgerRepository,
            PurchaseRepository purchaseRepository,
            PurchaseReturnRepository purchaseReturnRepository,
            PurchaseInventoryService inventoryService,
            PurchaseReturnInventoryService returnInventoryService,
            PurchasePricingEngine pricingEngine,
            PurchaseNumberAllocator numberAllocator,
            PurchaseReturnNumberAllocator returnNumberAllocator,
            SupplierPhoneNormalizer phoneNormalizer,
            PurchasingMapper mapper,
            AuditWriter auditWriter,
            StoreService storeService,
            Clock clock) {
        this.supplierRepository = supplierRepository;
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.purchaseRepository = purchaseRepository;
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.inventoryService = inventoryService;
        this.returnInventoryService = returnInventoryService;
        this.pricingEngine = pricingEngine;
        this.numberAllocator = numberAllocator;
        this.returnNumberAllocator = returnNumberAllocator;
        this.phoneNormalizer = phoneNormalizer;
        this.mapper = mapper;
        this.auditWriter = auditWriter;
        this.storeService = storeService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingPage<PurchasingResponses.SupplierResponse> searchSuppliers(
            String query, Boolean active, SupplierBalanceStatus requestedStatus, int page, int size) {
        validatePage(page, size);
        SupplierBalanceStatus status = requestedStatus == null
                ? SupplierBalanceStatus.ALL : requestedStatus;
        return PurchasingPage.from(
                supplierRepository.search(
                        searchPattern(query), active, status.name(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"))),
                mapper::toSupplier);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingResponses.SupplierResponse getSupplier(String supplierId) {
        return mapper.toSupplier(requireSupplier(supplierId));
    }

    @Override
    @Transactional
    public PurchasingResponses.SupplierResponse createSupplier(
            String actorUserId, PurchasingRequests.CreateSupplierRequest request) {
        String phone = phoneNormalizer.normalize(request.phone());
        String gstin = normalizeGstin(request.gstin());
        ensureUniqueSupplier(phone, gstin, null);
        Instant now = Instant.now(clock);
        Supplier supplier = Supplier.create(
                normalizeName(request.name()), phone, gstin,
                normalizeText(request.address()), normalizeText(request.notes()), now);
        supplierRepository.saveAndFlush(supplier);
        auditWriter.write(
                actorUserId, "SUPPLIER_CREATED", "SUPPLIER", supplier.getId(),
                Map.of("name", supplier.getName(), "phone", supplier.getPhone()));
        return mapper.toSupplier(supplier);
    }

    @Override
    @Transactional
    public PurchasingResponses.SupplierResponse updateSupplier(
            String actorUserId, String supplierId,
            PurchasingRequests.UpdateSupplierRequest request) {
        Supplier supplier = requireSupplier(supplierId);
        if (supplier.getVersion() != request.version()) {
            throw conflict(
                    "STALE_SUPPLIER_VERSION",
                    "Supplier details have changed. Refresh and try again.");
        }
        String phone = phoneNormalizer.normalize(request.phone());
        String gstin = normalizeGstin(request.gstin());
        ensureUniqueSupplier(phone, gstin, supplierId);
        supplier.update(
                normalizeName(request.name()), phone, gstin, normalizeText(request.address()),
                normalizeText(request.notes()), request.active(), Instant.now(clock));
        supplierRepository.flush();
        auditWriter.write(
                actorUserId, "SUPPLIER_UPDATED", "SUPPLIER", supplierId,
                Map.of("active", supplier.isActive()));
        return mapper.toSupplier(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingResponses.SummaryResponse getSummary() {
        BigDecimal total = balanceRepository.totalOutstanding();
        BigDecimal credit = balanceRepository.totalCredit();
        return new PurchasingResponses.SummaryResponse(
                total == null ? ZERO : total.setScale(2, RoundingMode.HALF_UP),
                credit == null ? ZERO : credit.setScale(2, RoundingMode.HALF_UP),
                balanceRepository.countByOutstandingAmountGreaterThan(ZERO),
                balanceRepository.countByCreditAmountGreaterThan(ZERO),
                supplierRepository.countByActiveTrue());
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingPage<PurchasingResponses.SupplierLedgerResponse> getSupplierStatement(
            String supplierId, int page, int size) {
        validatePage(page, size);
        if (!supplierRepository.existsById(supplierId)) {
            throw supplierNotFound();
        }
        return PurchasingPage.from(
                ledgerRepository.findBySupplier_IdOrderByOccurredAtDesc(
                        supplierId, PageRequest.of(page, size)),
                mapper::toLedger);
    }

    @Override
    @Transactional
    public PurchasingResponses.SupplierPaymentResponse paySupplier(
            String actorUserId, String supplierId, String idempotencyKey,
            PurchasingRequests.SupplierPaymentRequest request) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        SupplierLedgerEntry replay = ledgerRepository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (!replay.getSupplier().getId().equals(supplierId)) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "This idempotency key belongs to another payment.");
            }
            return mapper.toPayment(replay, true);
        }

        BigDecimal amount = money(request.amount());
        if (amount.signum() <= 0 || request.paymentMode() == null) {
            throw invalid("INVALID_SUPPLIER_PAYMENT", "Payment mode and a positive amount are required.");
        }
        SupplierPayableBalance balance = balanceRepository.findBySupplierIdForUpdate(supplierId)
                .orElseThrow(this::supplierNotFound);
        if (balance.getVersion() != request.balanceVersion()) {
            throw conflict("STALE_SUPPLIER_BALANCE", "Supplier balance has changed. Refresh and try again.");
        }
        if (amount.compareTo(balance.getOutstandingAmount()) > 0) {
            throw conflict("PAYMENT_EXCEEDS_DUE", "Payment cannot exceed the supplier outstanding amount.");
        }
        Instant now = Instant.now(clock);
        BigDecimal balanceAfter = balance.pay(amount, now);
        SupplierLedgerEntry entry = SupplierLedgerEntry.payment(
                balance.getSupplier(), amount, balanceAfter, balance.getCreditAmount(),
                request.paymentMode(), key,
                normalizeText(request.reference()), normalizeText(request.notes()), actorUserId, now);
        ledgerRepository.save(entry);
        balanceRepository.flush();
        auditWriter.write(
                actorUserId, "SUPPLIER_PAYMENT_RECORDED", "SUPPLIER", supplierId,
                Map.of("amount", amount, "paymentMode", request.paymentMode().name()));
        return mapper.toPayment(entry, false);
    }

    @Override
    @Transactional
    public PurchasingResponses.PurchaseResponse receivePurchase(
            String actorUserId, String idempotencyKey,
            PurchasingRequests.ReceivePurchaseRequest request) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        Purchase replay = purchaseRepository.findDetailedByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            return mapper.toPurchase(replay, true);
        }
        String supplierInvoiceNumber = normalizeText(request.supplierInvoiceNumber());
        if (supplierInvoiceNumber != null && purchaseRepository
                .existsBySupplier_IdAndSupplierInvoiceNumber(request.supplierId(), supplierInvoiceNumber)) {
            throw conflict("SUPPLIER_INVOICE_EXISTS", "This supplier invoice has already been received.");
        }

        SupplierPayableBalance balance = balanceRepository
                .findBySupplierIdForUpdate(request.supplierId())
                .orElseThrow(this::supplierNotFound);
        Supplier supplier = balance.getSupplier();
        if (!supplier.isActive()) {
            throw conflict("INACTIVE_SUPPLIER", "The selected supplier is inactive.");
        }

        String purchaseId = UUID.randomUUID().toString();
        List<PurchaseStockRequest> stockItems = request.items().stream()
                .map(item -> new PurchaseStockRequest(item.productId(), item.quantity(), item.unitCost()))
                .toList();
        var products = inventoryService.receivePurchase(actorUserId, purchaseId, stockItems);
        var pricing = pricingEngine.calculate(products, request.pricesIncludeTax());
        BigDecimal amountPaid = money(request.amountPaid());
        validateReceiptPayment(amountPaid, pricing.totalAmount(), request.paymentMode());

        Instant now = Instant.now(clock);
        Purchase purchase = Purchase.received(
                purchaseId, numberAllocator.next(), key, supplier, supplierInvoiceNumber,
                request.invoiceDate(), pricing, amountPaid, request.paymentMode(),
                normalizeText(request.paymentReference()), normalizeText(request.notes()), actorUserId, now);
        purchaseRepository.saveAndFlush(purchase);
        if (purchase.getOutstandingAdded().signum() > 0) {
            var movement = balance.applyPurchase(purchase.getOutstandingAdded(), now);
            ledgerRepository.save(SupplierLedgerEntry.purchaseDue(
                    supplier, purchase, purchase.getOutstandingAdded(), movement.payableAfter(),
                    movement.creditAfter(), actorUserId, now));
            balanceRepository.flush();
        }
        auditWriter.write(
                actorUserId, "PURCHASE_RECEIVED", "PURCHASE", purchaseId,
                Map.of("purchaseNumber", purchase.getPurchaseNumber(),
                        "totalAmount", purchase.getTotalAmount(), "supplierId", supplier.getId()));
        return mapper.toPurchase(purchase, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingResponses.PurchaseResponse getPurchase(String purchaseId) {
        Purchase purchase = purchaseRepository.findDetailedById(purchaseId)
                .orElseThrow(this::purchaseNotFound);
        return mapper.toPurchase(purchase, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingPage<PurchasingResponses.PurchaseSummaryResponse> searchPurchases(
            String query, String supplierId, LocalDate from, LocalDate to, int page, int size) {
        validatePage(page, size);
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                throw invalid("INVALID_PURCHASE_RANGE", "Purchase end date cannot be before start date.");
            }
            if (ChronoUnit.DAYS.between(from, to) + 1 > 366) {
                throw invalid("PURCHASE_RANGE_TOO_LARGE", "Purchase search can cover at most 366 days.");
            }
        }
        return PurchasingPage.from(
                purchaseRepository.search(
                        normalizeText(supplierId), from, to, searchPattern(query),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"))),
                mapper::toPurchaseSummary);
    }

    @Override
    @Transactional
    public PurchasingResponses.PurchaseReturnResponse returnPurchase(
            String actorUserId, String purchaseId, String idempotencyKey,
            PurchasingRequests.CreatePurchaseReturnRequest request) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        PurchaseReturn replay = purchaseReturnRepository.findDetailedByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (!replay.getPurchase().getId().equals(purchaseId)) {
                throw conflict(
                        "IDEMPOTENCY_KEY_REUSED",
                        "This idempotency key belongs to another purchase return.");
            }
            return mapper.toPurchaseReturn(replay, true);
        }

        Purchase purchase = purchaseRepository.findDetailedByIdForUpdate(purchaseId)
                .orElseThrow(this::purchaseNotFound);
        if (request.returnDate().isBefore(purchase.getInvoiceDate())) {
            throw invalid(
                    "INVALID_PURCHASE_RETURN_DATE",
                    "Return date cannot be before the supplier invoice date.");
        }
        SupplierPayableBalance balance = balanceRepository
                .findBySupplierIdForUpdate(purchase.getSupplier().getId())
                .orElseThrow(this::supplierNotFound);

        Map<String, PurchaseItem> sourceItems = new HashMap<>();
        purchase.getItems().forEach(item -> sourceItems.put(item.getId(), item));
        Map<String, Boolean> selectedIds = new HashMap<>();
        List<PurchaseReturnSelection> selections = request.items().stream().map(requested -> {
            String itemId = requested.purchaseItemId().trim();
            if (selectedIds.put(itemId, Boolean.TRUE) != null) {
                throw invalid(
                        "DUPLICATE_PURCHASE_RETURN_LINE",
                        "A purchase line can appear only once in a return.");
            }
            PurchaseItem source = sourceItems.get(itemId);
            if (source == null) {
                throw invalid(
                        "PURCHASE_RETURN_LINE_NOT_FOUND",
                        "A selected item does not belong to this purchase.");
            }
            BigDecimal quantity = returnQuantity(requested.quantity());
            if (quantity.compareTo(source.getReturnableQuantity()) > 0) {
                throw conflict(
                        "RETURN_QUANTITY_EXCEEDS_AVAILABLE",
                        source.getProductName() + " return quantity exceeds the remaining purchased quantity.");
            }
            return new PurchaseReturnSelection(source, quantity);
        }).toList();

        List<PurchaseProductSnapshot> snapshots = selections.stream()
                .map(selection -> new PurchaseProductSnapshot(
                        selection.purchaseItem().getProductId(),
                        selection.purchaseItem().getProductName(),
                        selection.purchaseItem().getUnit(),
                        selection.quantity(),
                        selection.purchaseItem().getUnitCost(),
                        selection.purchaseItem().getGstRate()))
                .toList();
        var pricing = pricingEngine.calculate(snapshots, purchase.isPricesIncludeTax());
        String returnId = UUID.randomUUID().toString();
        returnInventoryService.returnToSupplier(
                actorUserId,
                returnId,
                selections.stream().map(selection -> new PurchaseReturnStockRequest(
                        selection.purchaseItem().getProductId(),
                        selection.purchaseItem().getProductName(),
                        selection.quantity())).toList());

        Instant now = Instant.now(clock);
        var movement = balance.applyReturn(pricing.totalAmount(), now);
        selections.forEach(selection -> selection.purchaseItem().registerReturn(selection.quantity()));
        PurchaseReturn purchaseReturn = PurchaseReturn.completed(
                returnId, returnNumberAllocator.next(), key, purchase, request.returnDate(),
                request.reason(), pricing, selections, movement, normalizeText(request.notes()),
                actorUserId, now);
        purchaseReturnRepository.saveAndFlush(purchaseReturn);
        ledgerRepository.save(SupplierLedgerEntry.purchaseReturn(
                purchase.getSupplier(), purchaseReturn, purchaseReturn.getTotalAmount(),
                movement.payableAfter(), movement.creditAfter(), actorUserId, now));
        balanceRepository.flush();
        auditWriter.write(
                actorUserId, "PURCHASE_RETURN_COMPLETED", "PURCHASE_RETURN", returnId,
                Map.of("returnNumber", purchaseReturn.getReturnNumber(),
                        "purchaseId", purchaseId,
                        "totalAmount", purchaseReturn.getTotalAmount()));
        return mapper.toPurchaseReturn(purchaseReturn, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingResponses.PurchaseReturnResponse getPurchaseReturn(String purchaseReturnId) {
        return mapper.toPurchaseReturn(
                purchaseReturnRepository.findDetailedById(purchaseReturnId)
                        .orElseThrow(this::purchaseReturnNotFound),
                false);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingPage<PurchasingResponses.PurchaseReturnSummaryResponse> searchPurchaseReturns(
            String query, String supplierId, String purchaseId,
            LocalDate from, LocalDate to, int page, int size) {
        validatePage(page, size);
        validateOptionalRange(from, to, "purchase return");
        return PurchasingPage.from(
                purchaseReturnRepository.search(
                        normalizeText(supplierId), normalizeText(purchaseId), from, to,
                        searchPattern(query),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "returnedAt"))),
                mapper::toPurchaseReturnSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchasingResponses.SupplierAnalyticsResponse getSupplierAnalytics(
            LocalDate from, LocalDate to) {
        validateRequiredRange(from, to);
        ZoneId zone = requireZone(storeService.getStore().timezone());
        Map<String, BigDecimal> purchases = aggregate(purchaseRepository.totalBySupplier(from, to));
        Map<String, BigDecimal> returns = aggregate(purchaseReturnRepository.totalBySupplier(from, to));
        Map<String, BigDecimal> payments = aggregate(ledgerRepository.paymentsBySupplier(
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant()));

        List<PurchasingResponses.SupplierAnalyticsRowResponse> rows = supplierRepository
                .findAllDetailed().stream()
                .map(supplier -> {
                    BigDecimal purchaseTotal = purchases.getOrDefault(supplier.getId(), ZERO);
                    BigDecimal returnTotal = returns.getOrDefault(supplier.getId(), ZERO);
                    BigDecimal paymentTotal = payments.getOrDefault(supplier.getId(), ZERO);
                    return new PurchasingResponses.SupplierAnalyticsRowResponse(
                            supplier.getId(), supplier.getName(), purchaseTotal, returnTotal,
                            purchaseTotal.subtract(returnTotal), paymentTotal,
                            supplier.getPayableBalance().getOutstandingAmount(),
                            supplier.getPayableBalance().getCreditAmount());
                })
                .filter(row -> row.purchaseTotal().signum() != 0
                        || row.returnTotal().signum() != 0
                        || row.paymentTotal().signum() != 0
                        || row.outstandingAmount().signum() != 0
                        || row.creditAmount().signum() != 0)
                .toList();

        BigDecimal purchaseTotal = sum(purchases);
        BigDecimal returnTotal = sum(returns);
        return new PurchasingResponses.SupplierAnalyticsResponse(
                from, to, zone.getId(), purchaseTotal, returnTotal,
                purchaseTotal.subtract(returnTotal), sum(payments),
                normalizedMoney(balanceRepository.totalOutstanding()),
                normalizedMoney(balanceRepository.totalCredit()), rows, Instant.now(clock));
    }

    private void validateReceiptPayment(
            BigDecimal amountPaid, BigDecimal totalAmount,
            com.simplifiedbilling.purchasing.domain.SupplierPaymentMode mode) {
        if (amountPaid.signum() < 0 || amountPaid.compareTo(totalAmount) > 0) {
            throw invalid("INVALID_PURCHASE_PAYMENT", "Amount paid must be between zero and the purchase total.");
        }
        if (amountPaid.signum() > 0 && mode == null) {
            throw invalid("PURCHASE_PAYMENT_MODE_REQUIRED", "Select a payment mode for the paid amount.");
        }
        if (amountPaid.signum() == 0 && mode != null) {
            throw invalid("UNEXPECTED_PURCHASE_PAYMENT_MODE", "Payment mode must be empty when nothing was paid.");
        }
    }

    private BigDecimal returnQuantity(BigDecimal value) {
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

    private void validateOptionalRange(LocalDate from, LocalDate to, String label) {
        if (from == null || to == null) return;
        if (to.isBefore(from)) {
            throw invalid(
                    "INVALID_PURCHASE_RETURN_RANGE",
                    "The " + label + " end date cannot be before the start date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > 366) {
            throw invalid(
                    "PURCHASE_RETURN_RANGE_TOO_LARGE",
                    "A " + label + " search can cover at most 366 days.");
        }
    }

    private void validateRequiredRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw invalid("ANALYTICS_DATES_REQUIRED", "Both analytics dates are required.");
        }
        if (to.isBefore(from)) {
            throw invalid("INVALID_ANALYTICS_RANGE", "Analytics end date cannot be before start date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > 366) {
            throw invalid("ANALYTICS_RANGE_TOO_LARGE", "Analytics can cover at most 366 days.");
        }
    }

    private Map<String, BigDecimal> aggregate(List<SupplierAmountAggregate> values) {
        Map<String, BigDecimal> result = new HashMap<>();
        values.forEach(value -> result.put(value.getSupplierId(), normalizedMoney(value.getAmount())));
        return result;
    }

    private BigDecimal sum(Map<String, BigDecimal> values) {
        return values.values().stream().reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal normalizedMoney(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private ZoneId requireZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ApplicationException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_STORE_TIMEZONE",
                    "The configured store timezone is invalid.");
        }
    }

    private Supplier requireSupplier(String supplierId) {
        return supplierRepository.findDetailedById(supplierId).orElseThrow(this::supplierNotFound);
    }

    private void ensureUniqueSupplier(String phone, String gstin, String currentId) {
        boolean phoneExists = currentId == null
                ? supplierRepository.existsByPhone(phone)
                : supplierRepository.existsByPhoneAndIdNot(phone, currentId);
        if (phoneExists) {
            throw conflict("SUPPLIER_PHONE_EXISTS", "Another supplier already uses this phone number.");
        }
        if (gstin != null) {
            boolean gstinExists = currentId == null
                    ? supplierRepository.existsByGstin(gstin)
                    : supplierRepository.existsByGstinAndIdNot(gstin, currentId);
            if (gstinExists) {
                throw conflict("SUPPLIER_GSTIN_EXISTS", "Another supplier already uses this GSTIN.");
            }
        }
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) {
            throw invalid("INVALID_SUPPLIER_NAME", "Supplier name is required.");
        }
        return name;
    }

    private String normalizeGstin(String value) {
        String gstin = normalizeText(value);
        if (gstin == null) return null;
        gstin = gstin.toUpperCase(Locale.ROOT);
        if (!GSTIN.matcher(gstin).matches()) {
            throw invalid("INVALID_SUPPLIER_GSTIN", "Enter a valid 15-character GSTIN.");
        }
        return gstin;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw invalid("INVALID_MONEY_PRECISION", "An amount is required.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("INVALID_MONEY_PRECISION", "Amounts support at most two decimal places.");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw invalid(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 8 to 80 safe characters.");
        }
        return key;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String searchPattern(String value) {
        String text = normalizeText(value);
        return text == null ? null : "%" + text.toLowerCase(Locale.ROOT) + "%";
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("INVALID_PAGE_REQUEST", "Page must be non-negative and size must be between 1 and 100.");
        }
    }

    private ApplicationException supplierNotFound() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "The supplier does not exist.");
    }

    private ApplicationException purchaseNotFound() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND", "The purchase does not exist.");
    }

    private ApplicationException purchaseReturnNotFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                "PURCHASE_RETURN_NOT_FOUND",
                "The purchase return does not exist.");
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApplicationException conflict(String code, String message) {
        return new ApplicationException(HttpStatus.CONFLICT, code, message);
    }
}
