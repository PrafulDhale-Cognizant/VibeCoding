package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.pos.domain.Invoice;
import com.simplifiedbilling.pos.domain.Payment;
import com.simplifiedbilling.pos.dto.InvoiceQueryResponses;
import com.simplifiedbilling.pos.repository.InvoiceRepository;
import com.simplifiedbilling.pos.repository.InvoiceActivityStore;
import com.simplifiedbilling.pos.repository.SaleReturnRepository;
import com.simplifiedbilling.pos.service.InvoiceQueryService;
import com.simplifiedbilling.pos.service.InvoiceOutputType;
import com.simplifiedbilling.pos.service.InvoiceSearchCriteria;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class DefaultInvoiceQueryService implements InvoiceQueryService {
    private final InvoiceRepository invoiceRepository;
    private final SaleReturnRepository returnRepository;
    private final InvoiceActivityStore activityStore;
    private final AuditWriter auditWriter;

    public DefaultInvoiceQueryService(
            InvoiceRepository invoiceRepository,
            SaleReturnRepository returnRepository,
            InvoiceActivityStore activityStore,
            AuditWriter auditWriter) {
        this.invoiceRepository = invoiceRepository;
        this.returnRepository = returnRepository;
        this.activityStore = activityStore;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceQueryResponses.InvoicePage search(InvoiceSearchCriteria criteria) {
        int page = criteria.page();
        int size = criteria.size();
        if (page < 0 || size < 1 || size > 100) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                    "Page must be positive and size must be between 1 and 100.");
        }
        if (criteria.from() != null && criteria.to() != null && !criteria.from().isBefore(criteria.to())) {
            throw invalid("INVALID_DATE_RANGE", "The invoice start date must be before the end date.");
        }
        if (negative(criteria.minAmount()) || negative(criteria.maxAmount())
                || criteria.minAmount() != null && criteria.maxAmount() != null
                && criteria.minAmount().compareTo(criteria.maxAmount()) > 0) {
            throw invalid("INVALID_AMOUNT_RANGE", "Enter a valid non-negative invoice amount range.");
        }
        String query = criteria.query() == null ? "" : criteria.query().trim().toLowerCase();
        var invoices = invoiceRepository.searchInvoices(
                query, criteria.status(), criteria.paymentMode(), criteria.from(), criteria.to(),
                criteria.minAmount(), criteria.maxAmount(), PageRequest.of(page, size, invoiceSort(criteria.sort())));
        return new InvoiceQueryResponses.InvoicePage(
                invoices.getContent().stream().map(this::summary).toList(),
                invoices.getNumber(), invoices.getSize(), invoices.getTotalElements(), invoices.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceQueryResponses.InvoiceActivity> activity(String invoiceId) {
        requireInvoice(invoiceId);
        return activityStore.findByInvoiceId(invoiceId);
    }

    @Override
    @Transactional
    public void recordOutput(String actorUserId, String invoiceId, InvoiceOutputType outputType) {
        Invoice invoice = requireInvoice(invoiceId);
        if (outputType == null) throw invalid("OUTPUT_TYPE_REQUIRED", "Choose an invoice output type.");
        String eventType = switch (outputType) {
            case THERMAL_REPRINT -> "INVOICE_THERMAL_REPRINTED";
            case A4_PRINT -> "INVOICE_A4_PRINTED";
            case PDF_EXPORT -> "INVOICE_PDF_EXPORTED";
            case SHARE_COPIED -> "INVOICE_SHARE_COPIED";
        };
        auditWriter.write(actorUserId, eventType, "INVOICE", invoiceId,
                Map.of("invoiceNumber", invoice.getInvoiceNumber()));
    }

    private InvoiceQueryResponses.InvoiceSummary summary(Invoice invoice) {
        Payment customer = invoice.getPayments().stream()
                .filter(payment -> payment.getCustomerId() != null).findFirst().orElse(null);
        return new InvoiceQueryResponses.InvoiceSummary(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getStatus(), invoice.getCompletedAt(),
                invoice.getTotalAmount(), invoice.getTotalAmount().subtract(returnRepository.returnedTotal(invoice.getId())),
                customer == null ? null : customer.getCustomerId(),
                customer == null ? null : customer.getCustomerName(),
                customer == null ? null : customer.getCustomerPhone());
    }

    private Invoice requireInvoice(String invoiceId) {
        return invoiceRepository.findById(invoiceId).orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "The invoice does not exist."));
    }

    private Sort invoiceSort(String rawSort) {
        String value = rawSort == null ? "NEWEST" : rawSort.trim().toUpperCase();
        return switch (value) {
            case "NEWEST" -> Sort.by(Sort.Direction.DESC, "completedAt");
            case "OLDEST" -> Sort.by(Sort.Direction.ASC, "completedAt");
            case "AMOUNT_HIGH" -> Sort.by(Sort.Direction.DESC, "totalAmount");
            case "AMOUNT_LOW" -> Sort.by(Sort.Direction.ASC, "totalAmount");
            default -> throw invalid("INVALID_INVOICE_SORT", "Choose a supported invoice sort order.");
        };
    }

    private boolean negative(java.math.BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, code, message);
    }
}
