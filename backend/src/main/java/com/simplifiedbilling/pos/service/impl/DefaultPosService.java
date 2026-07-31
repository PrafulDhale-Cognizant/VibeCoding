package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.inventory.service.CheckoutInventoryService;
import com.simplifiedbilling.inventory.service.SaleProductSnapshot;
import com.simplifiedbilling.inventory.service.SaleStockRequest;
import com.simplifiedbilling.khata.service.CreditAccountService;
import com.simplifiedbilling.khata.service.CreditCustomerSnapshot;
import com.simplifiedbilling.pos.domain.Invoice;
import com.simplifiedbilling.pos.domain.PaymentAllocation;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.domain.PricingResult;
import com.simplifiedbilling.pos.dto.PosRequests;
import com.simplifiedbilling.pos.dto.PosResponses;
import com.simplifiedbilling.pos.mapper.PosMapper;
import com.simplifiedbilling.pos.repository.InvoiceRepository;
import com.simplifiedbilling.pos.service.InvoiceNumberAllocator;
import com.simplifiedbilling.pos.service.PosService;
import com.simplifiedbilling.pos.service.PricingEngine;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.service.StoreService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DefaultPosService implements PosService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,80}");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CheckoutInventoryService inventoryService;
    private final PricingEngine pricingEngine;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberAllocator numberAllocator;
    private final StoreService storeService;
    private final CreditAccountService creditAccountService;
    private final PosMapper mapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public DefaultPosService(
            CheckoutInventoryService inventoryService,
            PricingEngine pricingEngine,
            InvoiceRepository invoiceRepository,
            InvoiceNumberAllocator numberAllocator,
            StoreService storeService,
            CreditAccountService creditAccountService,
            PosMapper mapper,
            AuditWriter auditWriter,
            Clock clock) {
        this.inventoryService = inventoryService;
        this.pricingEngine = pricingEngine;
        this.invoiceRepository = invoiceRepository;
        this.numberAllocator = numberAllocator;
        this.storeService = storeService;
        this.creditAccountService = creditAccountService;
        this.mapper = mapper;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PosResponses.QuoteResponse quote(PosRequests.QuoteRequest request) {
        List<SaleProductSnapshot> products = inventoryService.getSaleProducts(
                request.items().stream().map(PosRequests.CartItemRequest::productId).toList());
        return mapper.toQuote(pricingEngine.calculate(request, products));
    }

    @Override
    @Transactional
    public PosResponses.InvoiceResponse checkout(
            String actorUserId,
            String idempotencyKey,
            PosRequests.CheckoutRequest request) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Invoice replay = invoiceRepository.findByIdempotencyKey(normalizedKey).orElse(null);
        if (replay != null) {
            return mapper.toInvoice(replay, storeService.getStore(), true);
        }

        String invoiceId = UUID.randomUUID().toString();
        List<SaleProductSnapshot> products = inventoryService.deductForSale(
                actorUserId,
                invoiceId,
                request.items().stream()
                        .map(item -> new SaleStockRequest(item.productId(), item.quantity()))
                        .toList());
        PricingResult pricing = pricingEngine.calculate(request.quoteRequest(), products);
        List<PaymentAllocation> payments = validatePayments(request.payments(), pricing.totalAmount());
        StoreDetails store = storeService.getStore();
        Instant now = Instant.now(clock);
        Invoice invoice = Invoice.completed(
                invoiceId,
                numberAllocator.next(store.invoicePrefix()),
                normalizedKey,
                actorUserId,
                pricing,
                payments,
                normalizeText(request.notes()),
                now);
        invoiceRepository.saveAndFlush(invoice);
        payments.stream()
                .filter(payment -> payment.mode() == PaymentMode.UDHAAR)
                .forEach(payment -> creditAccountService.postCreditSale(
                        actorUserId, payment.customerId(), invoiceId, payment.amount()));
        auditWriter.write(
                actorUserId,
                "SALE_COMPLETED",
                "INVOICE",
                invoiceId,
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "totalAmount", invoice.getTotalAmount()));
        return mapper.toInvoice(invoice, store, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PosResponses.InvoiceResponse getInvoice(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "INVOICE_NOT_FOUND",
                        "The invoice does not exist."));
        return mapper.toInvoice(invoice, storeService.getStore(), false);
    }

    private List<PaymentAllocation> validatePayments(
            List<PosRequests.PaymentRequest> requests,
            BigDecimal totalAmount) {
        if (requests == null || requests.isEmpty()) {
            throw invalid("PAYMENT_REQUIRED", "Add at least one payment.");
        }
        List<PaymentAllocation> allocations = requests.stream().map(request -> {
            if (request == null || request.mode() == null || request.amount() == null) {
                throw invalid("INVALID_PAYMENT", "Payment mode and amount are required.");
            }
            BigDecimal amount = money(request.amount());
            if (amount.signum() <= 0) {
                throw invalid("INVALID_PAYMENT", "Payment amount must be greater than zero.");
            }
            BigDecimal tendered = null;
            BigDecimal change = ZERO;
            String customerId = null;
            String customerName = null;
            if (request.mode() == PaymentMode.CASH) {
                tendered = money(request.tenderedAmount() == null ? amount : request.tenderedAmount());
                if (tendered.compareTo(amount) < 0) {
                    throw invalid("INSUFFICIENT_CASH", "Tendered cash cannot be less than the cash payment.");
                }
                change = tendered.subtract(amount);
            } else if (request.mode() == PaymentMode.UDHAAR) {
                if (request.customerId() == null || request.customerId().isBlank()) {
                    throw invalid("CREDIT_CUSTOMER_REQUIRED", "Select a customer for Udhaar payment.");
                }
                CreditCustomerSnapshot customer = creditAccountService.getCreditCustomer(request.customerId().trim());
                customerId = customer.customerId();
                customerName = customer.name();
            } else if (request.customerId() != null && !request.customerId().isBlank()) {
                throw invalid("INVALID_PAYMENT_CUSTOMER", "A customer can only be attached to Udhaar payment.");
            }
            return new PaymentAllocation(
                    request.mode(), amount, tendered, change, normalizeText(request.reference()),
                    customerId, customerName);
        }).toList();
        long creditPayments = allocations.stream()
                .filter(payment -> payment.mode() == PaymentMode.UDHAAR)
                .count();
        if (creditPayments > 1) {
            throw invalid("MULTIPLE_CREDIT_PAYMENTS", "A bill can contain only one Udhaar payment.");
        }
        BigDecimal paid = allocations.stream()
                .map(PaymentAllocation::amount)
                .reduce(ZERO, BigDecimal::add);
        if (paid.compareTo(totalAmount) != 0) {
            throw invalid("PAYMENT_TOTAL_MISMATCH", "Payment amounts must equal the bill total.");
        }
        return allocations;
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!IDEMPOTENCY_KEY.matcher(normalized).matches()) {
            throw invalid(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 8 to 80 letters, numbers, dots, colons, underscores or hyphens.");
        }
        return normalized;
    }

    private BigDecimal money(BigDecimal value) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("INVALID_MONEY_PRECISION", "Payment amounts support at most two decimal places.");
        }
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, code, message);
    }
}
