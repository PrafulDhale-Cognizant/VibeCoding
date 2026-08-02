package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.service.CheckoutInventoryService;
import com.simplifiedbilling.inventory.service.SaleProductSnapshot;
import com.simplifiedbilling.khata.service.CreditAccountService;
import com.simplifiedbilling.khata.service.CreditCustomerSnapshot;
import com.simplifiedbilling.pos.domain.DiscountType;
import com.simplifiedbilling.pos.domain.Invoice;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.domain.PricingLine;
import com.simplifiedbilling.pos.domain.PricingResult;
import com.simplifiedbilling.pos.domain.TaxMode;
import com.simplifiedbilling.pos.dto.PosRequests;
import com.simplifiedbilling.pos.dto.PosResponses;
import com.simplifiedbilling.pos.mapper.PosMapper;
import com.simplifiedbilling.pos.repository.InvoiceRepository;
import com.simplifiedbilling.pos.service.InvoiceNumberAllocator;
import com.simplifiedbilling.pos.service.PricingEngine;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.domain.ReceiptWidth;
import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPosServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final String KEY = "checkout-key-123";

    @Mock private CheckoutInventoryService inventoryService;
    @Mock private PricingEngine pricingEngine;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceNumberAllocator numberAllocator;
    @Mock private StoreService storeService;
    @Mock private CreditAccountService creditAccountService;
    @Mock private PosMapper mapper;
    @Mock private AuditWriter auditWriter;
    private DefaultPosService service;

    @BeforeEach
    void setUp() {
        service = new DefaultPosService(
                inventoryService, pricingEngine, invoiceRepository, numberAllocator,
                storeService, creditAccountService, mapper, auditWriter, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void quotesUsingInventoryModuleBoundary() {
        PosRequests.QuoteRequest request = quoteRequest();
        SaleProductSnapshot product = product();
        PricingResult pricing = pricing();
        StoreDetails store = store();
        PosResponses.QuoteResponse response = org.mockito.Mockito.mock(PosResponses.QuoteResponse.class);
        when(inventoryService.getSaleProducts(List.of("product-1"))).thenReturn(List.of(product));
        when(storeService.getStore()).thenReturn(store);
        when(pricingEngine.calculate(request, List.of(product), true)).thenReturn(pricing);
        when(mapper.toQuote(pricing)).thenReturn(response);

        assertThat(service.quote(request)).isSameAs(response);
    }

    @Test
    void disablesGstWhenShopHasNoConfiguredGstin() {
        PosRequests.QuoteRequest request = quoteRequest();
        SaleProductSnapshot product = product();
        PricingResult pricing = pricing();
        PosResponses.QuoteResponse response = org.mockito.Mockito.mock(PosResponses.QuoteResponse.class);
        when(inventoryService.getSaleProducts(List.of("product-1"))).thenReturn(List.of(product));
        when(storeService.getStore()).thenReturn(storeWithoutGst());
        when(pricingEngine.calculate(request, List.of(product), false)).thenReturn(pricing);
        when(mapper.toQuote(pricing)).thenReturn(response);

        assertThat(service.quote(request)).isSameAs(response);
    }

    @Test
    void returnsExistingInvoiceForIdempotentRetry() {
        Invoice invoice = org.mockito.Mockito.mock(Invoice.class);
        StoreDetails store = store();
        PosResponses.InvoiceResponse response = org.mockito.Mockito.mock(PosResponses.InvoiceResponse.class);
        when(invoiceRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(invoice));
        when(storeService.getStore()).thenReturn(store);
        when(mapper.toInvoice(invoice, store, true)).thenReturn(response);

        assertThat(service.checkout("actor", "  " + KEY + "  ", checkoutRequest(List.of(
                payment(PaymentMode.CASH, "100", "100", null))))).isSameAs(response);
    }

    @Test
    void completesCheckoutWithCashUpiAndCardAllocations() {
        prepareNewCheckout();
        StoreDetails store = store();
        PosResponses.InvoiceResponse response = org.mockito.Mockito.mock(PosResponses.InvoiceResponse.class);
        when(storeService.getStore()).thenReturn(store);
        when(numberAllocator.next("INV")).thenReturn("INV-000001");
        when(mapper.toInvoice(any(Invoice.class), eq(store), eq(false))).thenReturn(response);

        var result = service.checkout("actor", KEY, checkoutRequest(List.of(
                payment(PaymentMode.CASH, "40", "50", null),
                payment(PaymentMode.UPI, "30", null, " upi-1 "),
                payment(PaymentMode.CARD, "30", null, " "))));

        assertThat(result).isSameAs(response);
        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(saved.capture());
        Invoice invoice = saved.getValue();
        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-000001");
        assertThat(invoice.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(invoice.getCashierUserId()).isEqualTo("actor");
        assertThat(invoice.isGstApplied()).isTrue();
        assertThat(invoice.getNotes()).isEqualTo("Counter sale");
        assertThat(invoice.getPayments()).hasSize(3);
        assertThat(invoice.getPayments().getFirst().getTenderedAmount()).isEqualByComparingTo("50.00");
        assertThat(invoice.getPayments().getFirst().getChangeAmount()).isEqualByComparingTo("10.00");
        assertThat(invoice.getPayments().get(1).getReference()).isEqualTo("upi-1");
        assertThat(invoice.getPayments().get(2).getReference()).isNull();
        verify(auditWriter).write(eq("actor"), eq("SALE_COMPLETED"), eq("INVOICE"), eq(invoice.getId()), any());
    }

    @Test
    void completesUdhaarCheckoutAndPostsCustomerCreditAfterInvoiceSave() {
        prepareNewCheckout();
        StoreDetails store = store();
        PosResponses.InvoiceResponse response = org.mockito.Mockito.mock(PosResponses.InvoiceResponse.class);
        when(creditAccountService.getCreditCustomer("customer-1")).thenReturn(
                new CreditCustomerSnapshot("customer-1", "Ravi", "9876543210", new BigDecimal("25.00")));
        when(storeService.getStore()).thenReturn(store);
        when(numberAllocator.next("INV")).thenReturn("INV-000002");
        when(mapper.toInvoice(any(Invoice.class), eq(store), eq(false))).thenReturn(response);

        var request = checkoutRequest(List.of(new PosRequests.PaymentRequest(
                PaymentMode.UDHAAR, new BigDecimal("100.00"), null, null, "customer-1")));

        assertThat(service.checkout("actor", KEY, request)).isSameAs(response);
        ArgumentCaptor<Invoice> invoice = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(invoice.capture());
        assertThat(invoice.getValue().getPayments().getFirst().getCustomerName()).isEqualTo("Ravi");
        verify(creditAccountService).postCreditSale(
                "actor", "customer-1", invoice.getValue().getId(), new BigDecimal("100.00"));
    }

    @Test
    void attachesCustomerSnapshotToNonCreditPayment() {
        prepareNewCheckout();
        StoreDetails store = store();
        PosResponses.InvoiceResponse response = org.mockito.Mockito.mock(PosResponses.InvoiceResponse.class);
        when(creditAccountService.getCreditCustomer("customer-1")).thenReturn(
                new CreditCustomerSnapshot("customer-1", "Ravi", "9876543210", BigDecimal.ZERO));
        when(storeService.getStore()).thenReturn(store);
        when(numberAllocator.next("INV")).thenReturn("INV-000003");
        when(mapper.toInvoice(any(Invoice.class), eq(store), eq(false))).thenReturn(response);

        var request = checkoutRequest(List.of(new PosRequests.PaymentRequest(
                PaymentMode.UPI, new BigDecimal("100.00"), null, " upi-2 ", " customer-1 ")));

        assertThat(service.checkout("actor", KEY, request)).isSameAs(response);
        ArgumentCaptor<Invoice> invoice = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(invoice.capture());
        var payment = invoice.getValue().getPayments().getFirst();
        assertThat(payment.getMode()).isEqualTo(PaymentMode.UPI);
        assertThat(payment.getReference()).isEqualTo("upi-2");
        assertThat(payment.getCustomerId()).isEqualTo("customer-1");
        assertThat(payment.getCustomerName()).isEqualTo("Ravi");
        assertThat(payment.getCustomerPhone()).isEqualTo("9876543210");
        verify(creditAccountService, never()).postCreditSale(any(), any(), any(), any());
    }

    @Test
    void validatesIdempotencyAndPaymentRules() {
        assertError(() -> service.checkout("actor", "bad key", checkoutRequest(List.of(
                payment(PaymentMode.CASH, "100", "100", null)))), "INVALID_IDEMPOTENCY_KEY");

        prepareNewCheckout();
        assertError(() -> service.checkout("actor", KEY, checkoutRequest(List.of())), "PAYMENT_REQUIRED");
        assertError(() -> service.checkout("actor", KEY, checkoutRequest(Arrays.asList((PosRequests.PaymentRequest) null))), "INVALID_PAYMENT");
        assertError(() -> service.checkout("actor", KEY, checkoutRequest(List.of(
                new PosRequests.PaymentRequest(null, new BigDecimal("100"), null, null, null)))), "INVALID_PAYMENT");
        assertError(() -> service.checkout("actor", KEY, checkoutRequest(List.of(
                payment(PaymentMode.CASH, "0", "0", null)))), "INVALID_PAYMENT");
        assertError(() -> service.checkout("actor", KEY, checkoutRequest(List.of(
                payment(PaymentMode.CASH, "100", "90", null)))), "INSUFFICIENT_CASH");
        assertError(() -> service.checkout("actor", KEY, checkoutRequest(List.of(
                payment(PaymentMode.CASH, "99", "99", null)))), "PAYMENT_TOTAL_MISMATCH");
        assertError(() -> service.checkout("actor", KEY, checkoutRequest(List.of(
                payment(PaymentMode.CASH, "100.001", "101", null)))), "INVALID_MONEY_PRECISION");

        assertError(() -> service.checkout("actor", KEY, checkoutRequest(List.of(
                new PosRequests.PaymentRequest(
                        PaymentMode.UDHAAR, new BigDecimal("100"), null, null, null)))),
                "CREDIT_CUSTOMER_REQUIRED");

        when(creditAccountService.getCreditCustomer("customer-1")).thenReturn(
                new CreditCustomerSnapshot("customer-1", "Ravi", "9876543210", BigDecimal.ZERO));
        assertError(() -> service.checkout("actor", KEY, checkoutRequest(List.of(
                new PosRequests.PaymentRequest(
                        PaymentMode.UDHAAR, new BigDecimal("50"), null, null, "customer-1"),
                new PosRequests.PaymentRequest(
                        PaymentMode.UDHAAR, new BigDecimal("50"), null, null, "customer-1")))),
                "MULTIPLE_CREDIT_PAYMENTS");
    }

    @Test
    void retrievesInvoiceOrReturnsNotFound() {
        Invoice invoice = org.mockito.Mockito.mock(Invoice.class);
        StoreDetails store = store();
        PosResponses.InvoiceResponse response = org.mockito.Mockito.mock(PosResponses.InvoiceResponse.class);
        when(invoiceRepository.findById("invoice")).thenReturn(Optional.of(invoice));
        when(storeService.getStore()).thenReturn(store);
        when(mapper.toInvoice(invoice, store, false)).thenReturn(response);

        assertThat(service.getInvoice("invoice")).isSameAs(response);
        assertError(() -> service.getInvoice("missing"), "INVOICE_NOT_FOUND");
    }

    private void prepareNewCheckout() {
        PosRequests.QuoteRequest request = quoteRequest();
        SaleProductSnapshot product = product();
        when(invoiceRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(inventoryService.deductForSale(eq("actor"), any(), any())).thenReturn(List.of(product));
        when(storeService.getStore()).thenReturn(store());
        when(pricingEngine.calculate(eq(request), eq(List.of(product)), eq(true))).thenReturn(pricing());
    }

    private PosRequests.CheckoutRequest checkoutRequest(List<PosRequests.PaymentRequest> payments) {
        PosRequests.QuoteRequest quote = quoteRequest();
        return new PosRequests.CheckoutRequest(
                quote.items(), quote.billDiscountType(), quote.billDiscountValue(), quote.taxMode(),
                payments, " Counter sale ");
    }

    private PosRequests.QuoteRequest quoteRequest() {
        return new PosRequests.QuoteRequest(
                List.of(new PosRequests.CartItemRequest(
                        "product-1", BigDecimal.ONE, DiscountType.NONE, BigDecimal.ZERO)),
                DiscountType.NONE,
                BigDecimal.ZERO,
                TaxMode.INTRA_STATE);
    }

    private PosRequests.PaymentRequest payment(
            PaymentMode mode,
            String amount,
            String tendered,
            String reference) {
        return new PosRequests.PaymentRequest(
                mode,
                new BigDecimal(amount),
                tendered == null ? null : new BigDecimal(tendered),
                reference,
                null);
    }

    private SaleProductSnapshot product() {
        return new SaleProductSnapshot(
                "product-1", "Rice", "Rice", "1234", ProductUnit.PIECE,
                new BigDecimal("5.00"), new BigDecimal("80.00"),
                new BigDecimal("100.00"), new BigDecimal("9.000"));
    }

    private PricingResult pricing() {
        SaleProductSnapshot product = product();
        PricingLine line = new PricingLine(
                1, product, new BigDecimal("1.000"), new BigDecimal("100.00"),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                new BigDecimal("95.24"), new BigDecimal("2.38"), new BigDecimal("2.38"),
                BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"));
        return new PricingResult(
                List.of(line), TaxMode.INTRA_STATE, true, true, new BigDecimal("100.00"),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), new BigDecimal("95.24"),
                new BigDecimal("2.38"), new BigDecimal("2.38"), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"));
    }

    private StoreDetails store() {
        return new StoreDetails(
                "Shop", "Owner", "1 Main Road", null, "Pune", "Maharashtra", "27",
                "411001", "9999999999", null, true, "27ABCDE1234F1Z5", "INR",
                "Asia/Kolkata", "INV", 4, ReceiptWidth.MM_80, false, 0, NOW, NOW);
    }

    private StoreDetails storeWithoutGst() {
        return new StoreDetails(
                "Shop", "Owner", "1 Main Road", null, "Pune", "Maharashtra", "27",
                "411001", "9999999999", null, false, null, "INR",
                "Asia/Kolkata", "INV", 4, ReceiptWidth.MM_80, false, 0, NOW, NOW);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
