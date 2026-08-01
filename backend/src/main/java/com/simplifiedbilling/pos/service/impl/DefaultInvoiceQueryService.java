package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.pos.domain.Invoice;
import com.simplifiedbilling.pos.domain.Payment;
import com.simplifiedbilling.pos.dto.InvoiceQueryResponses;
import com.simplifiedbilling.pos.repository.InvoiceRepository;
import com.simplifiedbilling.pos.repository.SaleReturnRepository;
import com.simplifiedbilling.pos.service.InvoiceQueryService;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultInvoiceQueryService implements InvoiceQueryService {
    private final InvoiceRepository invoiceRepository;
    private final SaleReturnRepository returnRepository;

    public DefaultInvoiceQueryService(InvoiceRepository invoiceRepository, SaleReturnRepository returnRepository) {
        this.invoiceRepository = invoiceRepository;
        this.returnRepository = returnRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceQueryResponses.InvoicePage search(String rawQuery, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                    "Page must be positive and size must be between 1 and 100.");
        }
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        var invoices = invoiceRepository.searchInvoices(query,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "completedAt")));
        return new InvoiceQueryResponses.InvoicePage(
                invoices.getContent().stream().map(this::summary).toList(),
                invoices.getNumber(), invoices.getSize(), invoices.getTotalElements(), invoices.getTotalPages());
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
}
