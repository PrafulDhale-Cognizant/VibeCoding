package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.service.CheckoutInventoryService;
import com.simplifiedbilling.inventory.service.SaleReturnStockRequest;
import com.simplifiedbilling.khata.service.CreditAccountService;
import com.simplifiedbilling.pos.domain.Invoice;
import com.simplifiedbilling.pos.domain.InvoiceItem;
import com.simplifiedbilling.pos.domain.InvoiceStatus;
import com.simplifiedbilling.pos.domain.Payment;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.domain.ReturnDisposition;
import com.simplifiedbilling.pos.domain.SaleReturn;
import com.simplifiedbilling.pos.domain.SaleReturnType;
import com.simplifiedbilling.pos.dto.PosResponses;
import com.simplifiedbilling.pos.dto.SaleReturnRequests;
import com.simplifiedbilling.pos.dto.SaleReturnResponses;
import com.simplifiedbilling.pos.repository.InvoiceRepository;
import com.simplifiedbilling.pos.repository.RefundRecordRepository;
import com.simplifiedbilling.pos.repository.SaleReturnItemRepository;
import com.simplifiedbilling.pos.repository.SaleReturnLineTotals;
import com.simplifiedbilling.pos.repository.SaleReturnRepository;
import com.simplifiedbilling.pos.service.RefundAllocation;
import com.simplifiedbilling.pos.service.SaleReturnLineSelection;
import com.simplifiedbilling.pos.service.SaleReturnNumberAllocator;
import com.simplifiedbilling.pos.service.SaleReturnService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DefaultSaleReturnService implements SaleReturnService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,80}");

    private final InvoiceRepository invoiceRepository;
    private final SaleReturnRepository returnRepository;
    private final SaleReturnItemRepository returnItemRepository;
    private final RefundRecordRepository refundRepository;
    private final CheckoutInventoryService inventoryService;
    private final CreditAccountService creditAccountService;
    private final SaleReturnNumberAllocator numberAllocator;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public DefaultSaleReturnService(
            InvoiceRepository invoiceRepository, SaleReturnRepository returnRepository,
            SaleReturnItemRepository returnItemRepository, RefundRecordRepository refundRepository,
            CheckoutInventoryService inventoryService, CreditAccountService creditAccountService,
            SaleReturnNumberAllocator numberAllocator, AuditWriter auditWriter, Clock clock) {
        this.invoiceRepository = invoiceRepository; this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository; this.refundRepository = refundRepository;
        this.inventoryService = inventoryService; this.creditAccountService = creditAccountService;
        this.numberAllocator = numberAllocator; this.auditWriter = auditWriter; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SaleReturnResponses.SourceInvoice findSourceInvoice(String invoiceNumber) {
        String normalized = invoiceNumber == null ? "" : invoiceNumber.trim();
        Invoice invoice = invoiceRepository.findByInvoiceNumberIgnoreCase(normalized)
                .orElseThrow(this::invoiceNotFound);
        return toSourceInvoice(invoice);
    }

    @Override
    @Transactional
    public SaleReturnResponses.ReturnResponse returnItems(
            String actorUserId, String invoiceId, String idempotencyKey,
            SaleReturnRequests.CreateRequest request) {
        return process(actorUserId, invoiceId, idempotencyKey, SaleReturnType.RETURN,
                request.reason(), request.items(), request.refunds());
    }

    @Override
    @Transactional
    public SaleReturnResponses.ReturnResponse cancel(
            String actorUserId, String invoiceId, String idempotencyKey,
            SaleReturnRequests.CancellationRequest request) {
        Invoice invoice = lockInvoice(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.COMPLETED) {
            throw conflict("INVOICE_NOT_CANCELLABLE", "Only an untouched completed invoice can be cancelled.");
        }
        List<SaleReturnRequests.LineRequest> lines = invoice.getItems().stream()
                .map(item -> new SaleReturnRequests.LineRequest(
                        item.getId(), item.getQuantity(), ReturnDisposition.SALEABLE))
                .toList();
        return processLocked(actorUserId, invoice, idempotencyKey, SaleReturnType.CANCELLATION,
                request.reason(), lines, request.refunds());
    }

    private SaleReturnResponses.ReturnResponse process(
            String actorUserId, String invoiceId, String idempotencyKey, SaleReturnType type,
            String reason, List<SaleReturnRequests.LineRequest> lineRequests,
            List<SaleReturnRequests.RefundRequest> refundRequests) {
        String key = normalizeKey(idempotencyKey);
        SaleReturn replay = returnRepository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (!replay.getInvoice().getId().equals(invoiceId)) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "This idempotency key belongs to another return.");
            }
            return toResponse(replay, true);
        }
        return processLocked(actorUserId, lockInvoice(invoiceId), key, type, reason, lineRequests, refundRequests);
    }

    private SaleReturnResponses.ReturnResponse processLocked(
            String actorUserId, Invoice invoice, String rawKey, SaleReturnType type, String rawReason,
            List<SaleReturnRequests.LineRequest> lineRequests,
            List<SaleReturnRequests.RefundRequest> refundRequests) {
        String key = normalizeKey(rawKey);
        SaleReturn replay = returnRepository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) return toResponse(replay, true);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.RETURNED) {
            throw conflict("INVOICE_ALREADY_REVERSED", "The invoice has already been fully reversed.");
        }
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.isBlank()) throw invalid("RETURN_REASON_REQUIRED", "A return reason is required.");

        Map<String, InvoiceItem> source = new HashMap<>();
        invoice.getItems().forEach(item -> source.put(item.getId(), item));
        Map<String, SaleReturnRequests.LineRequest> requested = new LinkedHashMap<>();
        for (SaleReturnRequests.LineRequest request : lineRequests) {
            if (request == null || requested.put(request.invoiceItemId(), request) != null) {
                throw invalid("DUPLICATE_RETURN_LINE", "Each invoice line can appear only once.");
            }
        }
        List<SaleReturnLineSelection> selections = new ArrayList<>();
        Map<String, BigDecimal> returnedAfter = new HashMap<>();
        for (SaleReturnRequests.LineRequest request : requested.values()) {
            InvoiceItem item = source.get(request.invoiceItemId());
            if (item == null) throw invalid("INVALID_RETURN_LINE", "A selected line does not belong to this invoice.");
            BigDecimal quantity = quantity(request.quantity());
            SaleReturnLineTotals prior = returnItemRepository.returnedTotals(item.getId());
            BigDecimal remaining = item.getQuantity().subtract(prior.getQuantity());
            if (quantity.compareTo(remaining) > 0) {
                throw conflict("RETURN_QUANTITY_EXCEEDED", item.getProductName() + " has only "
                        + remaining.stripTrailingZeros().toPlainString() + " returnable.");
            }
            selections.add(calculate(item, prior, quantity, remaining, request.disposition()));
            returnedAfter.put(item.getId(), prior.getQuantity().add(quantity));
        }
        boolean allReturned = invoice.getItems().stream().allMatch(item ->
                returnedAfter.getOrDefault(item.getId(), returnItemRepository.returnedTotals(item.getId()).getQuantity())
                        .compareTo(item.getQuantity()) == 0);
        if (type == SaleReturnType.CANCELLATION && !allReturned) {
            throw invalid("INVALID_CANCELLATION", "Cancellation must reverse every invoice line.");
        }
        if (allReturned) selections = reconcileFinalTotal(invoice, selections);
        BigDecimal total = selections.stream().map(SaleReturnLineSelection::lineTotal).reduce(ZERO, BigDecimal::add);
        List<RefundAllocation> refunds = validateRefunds(invoice, refundRequests, total);

        String returnId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        SaleReturn saleReturn = SaleReturn.completed(
                returnId, numberAllocator.next(), key, invoice, type, reason, selections,
                refunds, actorUserId, now);
        inventoryService.restoreSaleableReturns(actorUserId, returnId, selections.stream()
                .filter(line -> line.disposition() == ReturnDisposition.SALEABLE)
                .map(line -> new SaleReturnStockRequest(line.invoiceItem().getProductId(), line.quantity()))
                .toList());
        returnRepository.saveAndFlush(saleReturn);
        for (RefundAllocation refund : refunds) {
            if (refund.mode() == PaymentMode.UDHAAR) {
                creditAccountService.reverseCreditSale(actorUserId, refund.customerId(), invoice.getId(),
                        returnId, refund.amount(), type == SaleReturnType.CANCELLATION);
            }
        }
        invoice.recordReturn(allReturned, type == SaleReturnType.CANCELLATION);
        invoiceRepository.flush();
        auditWriter.write(actorUserId,
                type == SaleReturnType.CANCELLATION ? "SALE_CANCELLED" : "SALE_RETURNED",
                "INVOICE", invoice.getId(), Map.of("saleReturnId", returnId, "amount", total));
        return toResponse(saleReturn, false);
    }

    private List<SaleReturnLineSelection> reconcileFinalTotal(
            Invoice invoice, List<SaleReturnLineSelection> selections) {
        BigDecimal target = invoice.getTotalAmount().subtract(returnRepository.returnedTotal(invoice.getId()));
        BigDecimal current = selections.stream().map(SaleReturnLineSelection::lineTotal).reduce(ZERO, BigDecimal::add);
        BigDecimal delta = target.subtract(current);
        if (delta.signum() == 0 || selections.isEmpty()) return selections;
        List<SaleReturnLineSelection> adjusted = new ArrayList<>(selections);
        SaleReturnLineSelection line = adjusted.get(adjusted.size() - 1);
        adjusted.set(adjusted.size() - 1, new SaleReturnLineSelection(
                line.invoiceItem(), line.quantity(), line.disposition(), line.grossAmount(),
                line.discountAmount(), line.taxableAmount(), line.cgstAmount(), line.sgstAmount(),
                line.igstAmount(), line.lineTotal().add(delta)));
        return adjusted;
    }

    private SaleReturnLineSelection calculate(
            InvoiceItem item, SaleReturnLineTotals prior, BigDecimal quantity,
            BigDecimal remaining, ReturnDisposition disposition) {
        if (disposition == null) throw invalid("RETURN_DISPOSITION_REQUIRED", "Choose saleable or damaged.");
        boolean finalQuantity = quantity.compareTo(remaining) == 0;
        BigDecimal discount = item.getLineDiscountAmount().add(item.getBillDiscountAmount());
        return new SaleReturnLineSelection(item, quantity, disposition,
                component(item.getGrossAmount(), prior.getGrossAmount(), item.getQuantity(), quantity, finalQuantity),
                component(discount, prior.getDiscountAmount(), item.getQuantity(), quantity, finalQuantity),
                component(item.getTaxableAmount(), prior.getTaxableAmount(), item.getQuantity(), quantity, finalQuantity),
                component(item.getCgstAmount(), prior.getCgstAmount(), item.getQuantity(), quantity, finalQuantity),
                component(item.getSgstAmount(), prior.getSgstAmount(), item.getQuantity(), quantity, finalQuantity),
                component(item.getIgstAmount(), prior.getIgstAmount(), item.getQuantity(), quantity, finalQuantity),
                component(item.getLineTotal(), prior.getLineTotal(), item.getQuantity(), quantity, finalQuantity));
    }

    private BigDecimal component(
            BigDecimal original, BigDecimal prior, BigDecimal originalQuantity,
            BigDecimal returnQuantity, boolean finalQuantity) {
        return finalQuantity ? money(original.subtract(prior))
                : money(original.multiply(returnQuantity).divide(originalQuantity, 8, RoundingMode.HALF_UP));
    }

    private List<RefundAllocation> validateRefunds(
            Invoice invoice, List<SaleReturnRequests.RefundRequest> requests, BigDecimal total) {
        if (requests == null || requests.isEmpty()) throw invalid("REFUND_REQUIRED", "Add at least one refund.");
        List<Payment> originalCredit = invoice.getPayments().stream()
                .filter(payment -> payment.getMode() == PaymentMode.UDHAAR).toList();
        long requestedCreditCount = requests.stream().filter(r -> r.mode() == PaymentMode.UDHAAR).count();
        if (requestedCreditCount > 1) throw invalid("MULTIPLE_UDHAAR_REFUNDS", "Use one Udhaar reversal per return.");
        List<RefundAllocation> refunds = requests.stream().map(request -> {
            BigDecimal amount = money(request.amount());
            if (amount.signum() <= 0) throw invalid("INVALID_REFUND_AMOUNT", "Refund amount must be positive.");
            String customerId = null;
            if (request.mode() == PaymentMode.UDHAAR) {
                if (originalCredit.isEmpty()) throw invalid("INVALID_UDHAAR_REFUND", "The invoice has no Udhaar payment.");
                customerId = originalCredit.get(0).getCustomerId();
                if (request.customerId() != null && !customerId.equals(request.customerId().trim())) {
                    throw invalid("INVALID_UDHAAR_CUSTOMER", "The refund customer does not match the original sale.");
                }
                BigDecimal originalAmount = originalCredit.stream().map(Payment::getAmount).reduce(ZERO, BigDecimal::add);
                if (refundRepository.refundedUdhaar(invoice.getId()).add(amount).compareTo(originalAmount) > 0) {
                    throw conflict("UDHAAR_REFUND_EXCEEDED", "Udhaar reversals cannot exceed the original credit payment.");
                }
            } else if (request.customerId() != null && !request.customerId().isBlank()) {
                throw invalid("INVALID_REFUND_CUSTOMER", "A customer is only used for Udhaar reversal.");
            }
            return new RefundAllocation(request.mode(), amount, normalizeText(request.reference()), customerId);
        }).toList();
        BigDecimal refunded = refunds.stream().map(RefundAllocation::amount).reduce(ZERO, BigDecimal::add);
        if (refunded.compareTo(total) != 0) {
            throw invalid("REFUND_TOTAL_MISMATCH", "Refund amounts must equal the return total of " + total + ".");
        }
        return refunds;
    }

    @Override
    @Transactional(readOnly = true)
    public SaleReturnResponses.ReturnResponse getReturn(String saleReturnId) {
        return toResponse(returnRepository.findDetailedById(saleReturnId)
                .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND,
                        "SALE_RETURN_NOT_FOUND", "The sale return does not exist.")), false);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryPage<SaleReturnResponses.ReturnSummary> searchReturns(String query, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("INVALID_PAGE", "Page must be non-negative and size must be between 1 and 100.");
        }
        String normalized = normalizeText(query);
        String pattern = normalized == null ? null : "%" + normalized.toLowerCase() + "%";
        return InventoryPage.from(returnRepository.search(pattern,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "returnedAt"))), this::toSummary);
    }

    private SaleReturnResponses.ReturnSummary toSummary(SaleReturn value) {
        return new SaleReturnResponses.ReturnSummary(value.getId(), value.getReturnNumber(),
                value.getInvoice().getId(), value.getInvoice().getInvoiceNumber(), value.getType(), value.getReason(),
                value.getTotalAmount(), value.getReturnedAt());
    }

    private SaleReturnResponses.SourceInvoice toSourceInvoice(Invoice invoice) {
        List<SaleReturnResponses.InvoiceLine> lines = invoice.getItems().stream().map(item -> {
            SaleReturnLineTotals totals = returnItemRepository.returnedTotals(item.getId());
            BigDecimal returned = totals.getQuantity();
            return new SaleReturnResponses.InvoiceLine(item.getId(), item.getLineNumber(), item.getProductId(),
                    item.getProductName(), item.getUnit(), item.getQuantity(), returned,
                    item.getQuantity().subtract(returned), item.getUnitPrice(), item.getLineTotal(),
                    totals.getLineTotal(), item.getLineTotal().subtract(totals.getLineTotal()));
        }).toList();
        List<PosResponses.PaymentResponse> payments = invoice.getPayments().stream().map(payment ->
                new PosResponses.PaymentResponse(payment.getMode(), payment.getAmount(), payment.getTenderedAmount(),
                        payment.getChangeAmount(), payment.getReference(), payment.getCustomerId(), payment.getCustomerName(),
                        payment.getCustomerPhone()))
                .toList();
        return new SaleReturnResponses.SourceInvoice(invoice.getId(), invoice.getInvoiceNumber(), invoice.getStatus(),
                invoice.getCompletedAt(), invoice.getTotalAmount(),
                invoice.getTotalAmount().subtract(returnRepository.returnedTotal(invoice.getId())), payments, lines);
    }

    private SaleReturnResponses.ReturnResponse toResponse(SaleReturn value, boolean replay) {
        return new SaleReturnResponses.ReturnResponse(value.getId(), value.getReturnNumber(),
                value.getInvoice().getId(), value.getInvoice().getInvoiceNumber(), value.getType(), value.getReason(),
                value.getSubtotalAmount(), value.getDiscountAmount(), value.getTaxableAmount(), value.getCgstAmount(),
                value.getSgstAmount(), value.getIgstAmount(), value.getTotalAmount(), value.getReturnedAt(),
                value.getItems().stream().map(item -> new SaleReturnResponses.ReturnLine(
                        item.getInvoiceItem().getId(), item.getLineNumber(), item.getProductId(), item.getProductName(),
                        item.getUnit(), item.getQuantity(), item.getDisposition(), item.getPurchaseCost(), item.getGrossAmount(),
                        item.getDiscountAmount(), item.getTaxableAmount(), item.getCgstAmount(), item.getSgstAmount(),
                        item.getIgstAmount(), item.getLineTotal())).toList(),
                value.getRefunds().stream().map(refund -> new SaleReturnResponses.Refund(
                        refund.getMode(), refund.getAmount(), refund.getReference(), refund.getCustomerId())).toList(), replay);
    }

    private Invoice lockInvoice(String id) {
        return invoiceRepository.findDetailedByIdForUpdate(id).orElseThrow(this::invoiceNotFound);
    }
    private String normalizeKey(String value) {
        String key = value == null ? "" : value.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) throw invalid("INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key must contain 8 to 80 safe characters.");
        return key;
    }
    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw invalid("INVALID_RETURN_QUANTITY", "Return quantity must be positive.");
        try { return value.setScale(3, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException exception) { throw invalid("INVALID_QUANTITY_PRECISION", "Quantities support three decimals."); }
    }
    private BigDecimal money(BigDecimal value) {
        if (value == null) throw invalid("INVALID_MONEY", "An amount is required.");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
    private String normalizeText(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ApplicationException invoiceNotFound() { return new ApplicationException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "The invoice does not exist."); }
    private ApplicationException invalid(String code, String message) { return new ApplicationException(HttpStatus.BAD_REQUEST, code, message); }
    private ApplicationException conflict(String code, String message) { return new ApplicationException(HttpStatus.CONFLICT, code, message); }
}
