package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.pos.domain.Invoice;
import com.simplifiedbilling.pos.domain.InvoiceStatus;
import com.simplifiedbilling.pos.domain.Payment;
import com.simplifiedbilling.pos.repository.InvoiceRepository;
import com.simplifiedbilling.pos.repository.InvoiceActivityStore;
import com.simplifiedbilling.pos.repository.SaleReturnRepository;
import com.simplifiedbilling.pos.service.InvoiceOutputType;
import com.simplifiedbilling.pos.service.InvoiceSearchCriteria;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultInvoiceQueryServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SaleReturnRepository returnRepository;
    @Mock private InvoiceActivityStore activityStore;
    @Mock private AuditWriter auditWriter;
    private DefaultInvoiceQueryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultInvoiceQueryService(invoiceRepository, returnRepository, activityStore, auditWriter);
    }

    @Test
    void validatesEveryPageBoundary() {
        assertInvalidPage(-1, 20);
        assertInvalidPage(0, 0);
        assertInvalidPage(0, 101);
    }

    @Test
    void treatsNullQueryAsEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        when(invoiceRepository.searchInvoices(eq(""), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = service.search(null, 0, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
    }

    @Test
    void normalizesQueryAndMapsInvoicesWithAndWithoutCustomerDetails() {
        Invoice customerInvoice = invoice(
                "invoice-1", "INV-0001", new BigDecimal("100.00"),
                List.of(payment(null, null, null), payment("customer-1", "Ravi", "9876543210")));
        Invoice counterInvoice = invoice(
                "invoice-2", "INV-0002", new BigDecimal("50.00"), List.of());
        Pageable pageable = PageRequest.of(1, 20);
        when(invoiceRepository.searchInvoices(eq("ravi"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customerInvoice, counterInvoice), pageable, 42));
        when(returnRepository.returnedTotal("invoice-1")).thenReturn(new BigDecimal("15.00"));
        when(returnRepository.returnedTotal("invoice-2")).thenReturn(BigDecimal.ZERO);

        var result = service.search("  RAVI  ", 1, 20);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(42);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().getFirst().returnableTotal()).isEqualByComparingTo("85.00");
        assertThat(result.content().getFirst().customerId()).isEqualTo("customer-1");
        assertThat(result.content().getFirst().customerName()).isEqualTo("Ravi");
        assertThat(result.content().getFirst().customerPhone()).isEqualTo("9876543210");
        assertThat(result.content().get(1).returnableTotal()).isEqualByComparingTo("50.00");
        assertThat(result.content().get(1).customerId()).isNull();
        assertThat(result.content().get(1).customerName()).isNull();
        assertThat(result.content().get(1).customerPhone()).isNull();

        ArgumentCaptor<Pageable> request = ArgumentCaptor.forClass(Pageable.class);
        verify(invoiceRepository).searchInvoices(eq("ravi"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), request.capture());
        assertThat(request.getValue().getPageNumber()).isEqualTo(1);
        assertThat(request.getValue().getPageSize()).isEqualTo(20);
        assertThat(request.getValue().getSort().getOrderFor("completedAt").isDescending()).isTrue();
    }

    @Test
    void appliesAdvancedFiltersAndAmountSort() {
        Pageable pageable = PageRequest.of(0, 25);
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-03T00:00:00Z");
        when(invoiceRepository.searchInvoices(
                eq("inv"), eq(InvoiceStatus.COMPLETED), eq(com.simplifiedbilling.pos.domain.PaymentMode.CARD),
                eq(from), eq(to), eq(new BigDecimal("10")), eq(new BigDecimal("500")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.search(new InvoiceSearchCriteria(
                " INV ", InvoiceStatus.COMPLETED, com.simplifiedbilling.pos.domain.PaymentMode.CARD,
                from, to, new BigDecimal("10"), new BigDecimal("500"), "AMOUNT_HIGH", 0, 25));

        ArgumentCaptor<Pageable> request = ArgumentCaptor.forClass(Pageable.class);
        verify(invoiceRepository).searchInvoices(
                eq("inv"), eq(InvoiceStatus.COMPLETED), eq(com.simplifiedbilling.pos.domain.PaymentMode.CARD),
                eq(from), eq(to), eq(new BigDecimal("10")), eq(new BigDecimal("500")), request.capture());
        assertThat(request.getValue().getSort().getOrderFor("totalAmount").isDescending()).isTrue();
    }

    @Test
    void validatesAdvancedFiltersAndSort() {
        assertThatThrownBy(() -> service.search(new InvoiceSearchCriteria(
                "", null, null, Instant.parse("2026-08-03T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"), null, null, "NEWEST", 0, 20)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_DATE_RANGE"));
        assertThatThrownBy(() -> service.search(new InvoiceSearchCriteria(
                "", null, null, null, null, new BigDecimal("20"), new BigDecimal("10"), "NEWEST", 0, 20)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_AMOUNT_RANGE"));
        assertThatThrownBy(() -> service.search(new InvoiceSearchCriteria(
                "", null, null, null, null, null, null, "UNKNOWN", 0, 20)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_INVOICE_SORT"));
    }

    @Test
    void supportsEveryInvoiceSortDirection() {
        when(invoiceRepository.searchInvoices(eq(""), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(7), 0));

        service.search(new InvoiceSearchCriteria(
                "", null, null, null, null, null, null, "OLDEST", 0, 20));
        service.search(new InvoiceSearchCriteria(
                "", null, null, null, null, null, null, "AMOUNT_LOW", 0, 20));

        ArgumentCaptor<Pageable> requests = ArgumentCaptor.forClass(Pageable.class);
        verify(invoiceRepository, times(2)).searchInvoices(
                eq(""), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), requests.capture());
        assertThat(requests.getAllValues().get(0).getSort().getOrderFor("completedAt").isAscending()).isTrue();
        assertThat(requests.getAllValues().get(1).getSort().getOrderFor("totalAmount").isAscending()).isTrue();
    }

    @Test
    void recordsAndReturnsInvoiceActivity() {
        Invoice source = mock(Invoice.class);
        when(source.getInvoiceNumber()).thenReturn("INV-0001");
        when(invoiceRepository.findById("invoice-1")).thenReturn(java.util.Optional.of(source));
        var activity = new com.simplifiedbilling.pos.dto.InvoiceQueryResponses.InvoiceActivity(
                "SALE_COMPLETED", "Owner", Instant.parse("2026-08-01T10:00:00Z"));
        when(activityStore.findByInvoiceId("invoice-1")).thenReturn(List.of(activity));

        service.recordOutput("user-1", "invoice-1", InvoiceOutputType.THERMAL_REPRINT);
        assertThat(service.activity("invoice-1")).containsExactly(activity);

        verify(auditWriter).write("user-1", "INVOICE_THERMAL_REPRINTED", "INVOICE", "invoice-1",
                Map.of("invoiceNumber", "INV-0001"));
    }

    @Test
    void rejectsOutputForMissingInvoice() {
        when(invoiceRepository.findById("missing")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.recordOutput("user-1", "missing", InvoiceOutputType.PDF_EXPORT))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVOICE_NOT_FOUND"));
    }

    @Test
    void recordsEverySupportedInvoiceOutputAndRejectsMissingType() {
        Invoice source = mock(Invoice.class);
        when(source.getInvoiceNumber()).thenReturn("INV-0001");
        when(invoiceRepository.findById("invoice-1")).thenReturn(java.util.Optional.of(source));

        service.recordOutput("user-1", "invoice-1", InvoiceOutputType.A4_PRINT);
        service.recordOutput("user-1", "invoice-1", InvoiceOutputType.PDF_EXPORT);
        service.recordOutput("user-1", "invoice-1", InvoiceOutputType.SHARE_COPIED);

        verify(auditWriter).write("user-1", "INVOICE_A4_PRINTED", "INVOICE", "invoice-1",
                Map.of("invoiceNumber", "INV-0001"));
        verify(auditWriter).write("user-1", "INVOICE_PDF_EXPORTED", "INVOICE", "invoice-1",
                Map.of("invoiceNumber", "INV-0001"));
        verify(auditWriter).write("user-1", "INVOICE_SHARE_COPIED", "INVOICE", "invoice-1",
                Map.of("invoiceNumber", "INV-0001"));
        assertThatThrownBy(() -> service.recordOutput("user-1", "invoice-1", null))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("OUTPUT_TYPE_REQUIRED"));
    }

    private Invoice invoice(String id, String number, BigDecimal total, List<Payment> payments) {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(id);
        when(invoice.getInvoiceNumber()).thenReturn(number);
        when(invoice.getStatus()).thenReturn(InvoiceStatus.COMPLETED);
        when(invoice.getCompletedAt()).thenReturn(Instant.parse("2026-08-01T10:00:00Z"));
        when(invoice.getTotalAmount()).thenReturn(total);
        when(invoice.getPayments()).thenReturn(payments);
        return invoice;
    }

    private Payment payment(String customerId, String customerName, String customerPhone) {
        Payment payment = mock(Payment.class);
        when(payment.getCustomerId()).thenReturn(customerId);
        if (customerId != null) {
            when(payment.getCustomerName()).thenReturn(customerName);
            when(payment.getCustomerPhone()).thenReturn(customerPhone);
        }
        return payment;
    }

    private void assertInvalidPage(int page, int size) {
        assertThatThrownBy(() -> service.search("", page, size))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_PAGE");
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                });
    }
}
