package com.simplifiedbilling.purchasing.service.impl;

import com.simplifiedbilling.inventory.service.PurchaseInventoryService;
import com.simplifiedbilling.inventory.service.PurchaseStockRequest;
import com.simplifiedbilling.purchasing.domain.Purchase;
import com.simplifiedbilling.purchasing.domain.Supplier;
import com.simplifiedbilling.purchasing.domain.SupplierBalanceStatus;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntry;
import com.simplifiedbilling.purchasing.domain.SupplierPayableBalance;
import com.simplifiedbilling.purchasing.dto.PurchasingPage;
import com.simplifiedbilling.purchasing.dto.PurchasingRequests;
import com.simplifiedbilling.purchasing.dto.PurchasingResponses;
import com.simplifiedbilling.purchasing.mapper.PurchasingMapper;
import com.simplifiedbilling.purchasing.repository.PurchaseRepository;
import com.simplifiedbilling.purchasing.repository.SupplierLedgerRepository;
import com.simplifiedbilling.purchasing.repository.SupplierPayableBalanceRepository;
import com.simplifiedbilling.purchasing.repository.SupplierRepository;
import com.simplifiedbilling.purchasing.service.PurchaseNumberAllocator;
import com.simplifiedbilling.purchasing.service.PurchasePricingEngine;
import com.simplifiedbilling.purchasing.service.PurchasingService;
import com.simplifiedbilling.purchasing.service.SupplierPhoneNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
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
import java.time.temporal.ChronoUnit;
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
    private final PurchaseInventoryService inventoryService;
    private final PurchasePricingEngine pricingEngine;
    private final PurchaseNumberAllocator numberAllocator;
    private final SupplierPhoneNormalizer phoneNormalizer;
    private final PurchasingMapper mapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public DefaultPurchasingService(
            SupplierRepository supplierRepository,
            SupplierPayableBalanceRepository balanceRepository,
            SupplierLedgerRepository ledgerRepository,
            PurchaseRepository purchaseRepository,
            PurchaseInventoryService inventoryService,
            PurchasePricingEngine pricingEngine,
            PurchaseNumberAllocator numberAllocator,
            SupplierPhoneNormalizer phoneNormalizer,
            PurchasingMapper mapper,
            AuditWriter auditWriter,
            Clock clock) {
        this.supplierRepository = supplierRepository;
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.purchaseRepository = purchaseRepository;
        this.inventoryService = inventoryService;
        this.pricingEngine = pricingEngine;
        this.numberAllocator = numberAllocator;
        this.phoneNormalizer = phoneNormalizer;
        this.mapper = mapper;
        this.auditWriter = auditWriter;
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
        return new PurchasingResponses.SummaryResponse(
                total == null ? ZERO : total.setScale(2, RoundingMode.HALF_UP),
                balanceRepository.countByOutstandingAmountGreaterThan(ZERO),
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
                balance.getSupplier(), amount, balanceAfter, request.paymentMode(), key,
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
            BigDecimal balanceAfter = balance.addPayable(purchase.getOutstandingAdded(), now);
            ledgerRepository.save(SupplierLedgerEntry.purchaseDue(
                    supplier, purchase, purchase.getOutstandingAdded(), balanceAfter, actorUserId, now));
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

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApplicationException conflict(String code, String message) {
        return new ApplicationException(HttpStatus.CONFLICT, code, message);
    }
}
