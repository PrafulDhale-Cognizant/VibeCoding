package com.simplifiedbilling.khata.service.impl;

import com.simplifiedbilling.khata.domain.BalanceStatus;
import com.simplifiedbilling.khata.domain.Customer;
import com.simplifiedbilling.khata.domain.KhataEntryType;
import com.simplifiedbilling.khata.domain.KhataLedgerEntry;
import com.simplifiedbilling.khata.domain.SettlementMode;
import com.simplifiedbilling.khata.dto.KhataRequests;
import com.simplifiedbilling.khata.mapper.KhataMapper;
import com.simplifiedbilling.khata.repository.CustomerCreditBalanceRepository;
import com.simplifiedbilling.khata.repository.CustomerRepository;
import com.simplifiedbilling.khata.repository.KhataLedgerRepository;
import com.simplifiedbilling.khata.service.CustomerPhoneNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class DefaultKhataServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final String KEY = "settlement-key-1";

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerCreditBalanceRepository balanceRepository;
    @Mock private KhataLedgerRepository ledgerRepository;
    @Mock private AuditWriter auditWriter;
    private DefaultKhataService service;

    @BeforeEach
    void setUp() {
        service = new DefaultKhataService(
                customerRepository, balanceRepository, ledgerRepository,
                new CustomerPhoneNormalizer(), new KhataMapper(), auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void searchesCustomersWithNormalizedFiltersAndPagesResults() {
        Customer customer = customer("Ravi", true, "25.00");
        when(customerRepository.search(eq("%ravi%"), eq(true), eq("DUE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));

        var result = service.searchCustomers(" Ravi ", true, BalanceStatus.DUE, 0, 25);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().name()).isEqualTo("Ravi");
        assertThat(result.content().getFirst().outstandingAmount()).isEqualByComparingTo("25.00");

        when(customerRepository.search(eq(null), eq(null), eq("ALL"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.searchCustomers(" ", null, null, 0, 10).content()).isEmpty();
        assertError(() -> service.searchCustomers(null, true, BalanceStatus.ALL, -1, 10), "INVALID_PAGE_REQUEST");
        assertError(() -> service.searchCustomers(null, true, BalanceStatus.ALL, 0, 101), "INVALID_PAGE_REQUEST");
    }

    @Test
    void createsAndRetrievesNormalizedCustomer() {
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);

        var response = service.createCustomer(
                "actor",
                new KhataRequests.CreateCustomerRequest(
                        " Ravi Kumar ", "+91 98765 43210", " Regular customer "));

        verify(customerRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Ravi Kumar");
        assertThat(saved.getValue().getPhone()).isEqualTo("9876543210");
        assertThat(saved.getValue().getNotes()).isEqualTo("Regular customer");
        assertThat(response.outstandingAmount()).isZero();
        verify(auditWriter).write(eq("actor"), eq("CUSTOMER_CREATED"), eq("CUSTOMER"), eq(response.id()), any());

        when(customerRepository.findDetailedById(response.id())).thenReturn(Optional.of(saved.getValue()));
        assertThat(service.getCustomer(response.id()).name()).isEqualTo("Ravi Kumar");
        assertError(() -> service.getCustomer("missing"), "CUSTOMER_NOT_FOUND");
    }

    @Test
    void rejectsBlankNameAndDuplicatePhone() {
        assertError(() -> service.createCustomer(
                "actor", new KhataRequests.CreateCustomerRequest(" ", "9876543210", null)),
                "INVALID_CUSTOMER_NAME");
        when(customerRepository.existsByPhone("9876543210")).thenReturn(true);
        assertError(() -> service.createCustomer(
                "actor", new KhataRequests.CreateCustomerRequest("Ravi", "9876543210", null)),
                "CUSTOMER_PHONE_EXISTS");
    }

    @Test
    void updatesCustomerWithOptimisticVersionAndUniquePhone() {
        Customer customer = customer("Ravi", true, "0");
        when(customerRepository.findDetailedById(customer.getId())).thenReturn(Optional.of(customer));

        var response = service.updateCustomer(
                "actor", customer.getId(),
                new KhataRequests.UpdateCustomerRequest(
                        " Ravi K ", "99999 99999", " ", false, 0));

        assertThat(response.name()).isEqualTo("Ravi K");
        assertThat(response.phone()).isEqualTo("9999999999");
        assertThat(response.notes()).isNull();
        assertThat(response.active()).isFalse();
        verify(customerRepository).flush();
        verify(auditWriter).write(eq("actor"), eq("CUSTOMER_UPDATED"), eq("CUSTOMER"), eq(customer.getId()), any());

        assertError(() -> service.updateCustomer(
                "actor", customer.getId(),
                new KhataRequests.UpdateCustomerRequest("Ravi", "9999999999", null, true, 4)),
                "STALE_CUSTOMER_VERSION");
    }

    @Test
    void rejectsDuplicatePhoneDuringUpdate() {
        Customer customer = customer("Ravi", true, "0");
        when(customerRepository.findDetailedById(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepository.existsByPhoneAndIdNot("9999999999", customer.getId())).thenReturn(true);

        assertError(() -> service.updateCustomer(
                "actor", customer.getId(),
                new KhataRequests.UpdateCustomerRequest("Ravi", "9999999999", null, true, 0)),
                "CUSTOMER_PHONE_EXISTS");
    }

    @Test
    void pagesImmutableStatementAndValidatesCustomer() {
        Customer customer = customer("Ravi", true, "100");
        KhataLedgerEntry entry = KhataLedgerEntry.creditSale(
                customer, new BigDecimal("100.00"), new BigDecimal("100.00"),
                "invoice", "actor", NOW);
        when(customerRepository.existsById(customer.getId())).thenReturn(true);
        when(ledgerRepository.findByCustomer_IdOrderByOccurredAtDesc(
                eq(customer.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        var statement = service.getStatement(customer.getId(), 0, 50);

        assertThat(statement.content()).extracting("entryType")
                .containsExactly(KhataEntryType.CREDIT_SALE);
        assertError(() -> service.getStatement(customer.getId(), 0, 0), "INVALID_PAGE_REQUEST");
        assertError(() -> service.getStatement("missing", 0, 50), "CUSTOMER_NOT_FOUND");
    }

    @Test
    void recordsLockedPartialSettlementAndAuditsIt() {
        Customer customer = customer("Ravi", true, "100");
        when(ledgerRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(balanceRepository.findByCustomerIdForUpdate(customer.getId()))
                .thenReturn(Optional.of(customer.getCreditBalance()));

        var response = service.settle(
                "actor", customer.getId(), KEY,
                settlement("40.00", SettlementMode.UPI, 0));

        assertThat(response.amount()).isEqualByComparingTo("40.00");
        assertThat(response.balanceAfter()).isEqualByComparingTo("60.00");
        assertThat(response.idempotentReplay()).isFalse();
        ArgumentCaptor<KhataLedgerEntry> saved = ArgumentCaptor.forClass(KhataLedgerEntry.class);
        verify(ledgerRepository).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo(KEY);
        assertThat(saved.getValue().getPaymentReference()).isEqualTo("UPI-1");
        assertThat(saved.getValue().getNotes()).isEqualTo("Partial");
        verify(balanceRepository).flush();
        verify(auditWriter).write(eq("actor"), eq("KHATA_SETTLED"), eq("CUSTOMER"), eq(customer.getId()), any());
    }

    @Test
    void settlementRetryReturnsOriginalEntryAndRejectsKeyReuse() {
        Customer customer = customer("Ravi", true, "100");
        KhataLedgerEntry entry = KhataLedgerEntry.settlement(
                customer, new BigDecimal("20.00"), new BigDecimal("80.00"),
                SettlementMode.CASH, KEY, null, null, "actor", NOW);
        when(ledgerRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(entry));

        assertThat(service.settle("actor", customer.getId(), KEY, settlement("20", SettlementMode.CASH, 0))
                .idempotentReplay()).isTrue();

        Customer other = customer("Other", true, "0");
        assertError(() -> service.settle(
                "actor", other.getId(), KEY, settlement("20", SettlementMode.CASH, 0)),
                "IDEMPOTENCY_KEY_REUSED");
        verify(balanceRepository, never()).findByCustomerIdForUpdate(any());
    }

    @Test
    void validatesSettlementKeyMoneyModeVersionAndOutstandingAmount() {
        Customer customer = customer("Ravi", true, "100");
        assertError(() -> service.settle(
                "actor", customer.getId(), "bad key", settlement("10", SettlementMode.CASH, 0)),
                "INVALID_IDEMPOTENCY_KEY");

        when(ledgerRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        assertError(() -> service.settle(
                "actor", customer.getId(), KEY, settlement("1.001", SettlementMode.CASH, 0)),
                "INVALID_MONEY_PRECISION");
        assertError(() -> service.settle(
                "actor", customer.getId(), KEY, settlement("1", null, 0)),
                "INVALID_SETTLEMENT_MODE");
        assertError(() -> service.settle(
                "actor", customer.getId(), KEY, settlement("0", SettlementMode.CASH, 0)),
                "INVALID_SETTLEMENT_AMOUNT");

        when(balanceRepository.findByCustomerIdForUpdate(customer.getId()))
                .thenReturn(Optional.of(customer.getCreditBalance()));
        assertError(() -> service.settle(
                "actor", customer.getId(), KEY, settlement("10", SettlementMode.CASH, 3)),
                "STALE_KHATA_BALANCE");
        assertError(() -> service.settle(
                "actor", customer.getId(), KEY, settlement("101", SettlementMode.CASH, 0)),
                "SETTLEMENT_EXCEEDS_DUE");
        assertError(() -> service.settle(
                "actor", "missing", KEY, settlement("10", SettlementMode.CASH, 0)),
                "CUSTOMER_NOT_FOUND");
    }

    @Test
    void returnsKhataSummaryIncludingEmptyFallback() {
        when(balanceRepository.totalOutstanding()).thenReturn(new BigDecimal("250.5"));
        when(balanceRepository.countByOutstandingAmountGreaterThan(BigDecimal.ZERO.setScale(2))).thenReturn(2L);
        when(customerRepository.countByActiveTrue()).thenReturn(4L);

        var summary = service.getSummary();

        assertThat(summary.totalOutstanding()).isEqualByComparingTo("250.50");
        assertThat(summary.customersWithDue()).isEqualTo(2);
        assertThat(summary.activeCustomers()).isEqualTo(4);

        when(balanceRepository.totalOutstanding()).thenReturn(null);
        assertThat(service.getSummary().totalOutstanding()).isZero();
    }

    @Test
    void resolvesOnlyActiveCreditCustomers() {
        Customer customer = customer("Ravi", true, "25");
        when(customerRepository.findDetailedById(customer.getId())).thenReturn(Optional.of(customer));

        var snapshot = service.getCreditCustomer(customer.getId());

        assertThat(snapshot.name()).isEqualTo("Ravi");
        assertThat(snapshot.phone()).isEqualTo("9876543210");
        assertThat(snapshot.outstandingAmount()).isEqualByComparingTo("25.00");

        Customer inactive = customer("Old", false, "10");
        when(customerRepository.findDetailedById(inactive.getId())).thenReturn(Optional.of(inactive));
        assertError(() -> service.getCreditCustomer(inactive.getId()), "INACTIVE_CUSTOMER");
    }

    @Test
    void postsCreditSaleUnderLockAndIgnoresExistingInvoice() {
        Customer customer = customer("Ravi", true, "25");
        when(balanceRepository.findByCustomerIdForUpdate(customer.getId()))
                .thenReturn(Optional.of(customer.getCreditBalance()));

        service.postCreditSale("actor", customer.getId(), "invoice-1", new BigDecimal("75.00"));

        assertThat(customer.getCreditBalance().getOutstandingAmount()).isEqualByComparingTo("100.00");
        ArgumentCaptor<KhataLedgerEntry> saved = ArgumentCaptor.forClass(KhataLedgerEntry.class);
        verify(ledgerRepository).save(saved.capture());
        assertThat(saved.getValue().getInvoiceId()).isEqualTo("invoice-1");
        assertThat(saved.getValue().getEntryType()).isEqualTo(KhataEntryType.CREDIT_SALE);
        verify(balanceRepository).flush();
        verify(auditWriter).write(eq("actor"), eq("KHATA_CREDIT_POSTED"), eq("CUSTOMER"), eq(customer.getId()), any());

        when(ledgerRepository.existsByInvoiceId("invoice-2")).thenReturn(true);
        service.postCreditSale("actor", customer.getId(), "invoice-2", new BigDecimal("10"));
        verify(balanceRepository, never()).findByCustomerIdForUpdate(eq("invoice-2"));
    }

    @Test
    void rejectsInvalidOrInactiveCreditSale() {
        assertError(() -> service.postCreditSale("actor", "customer", "invoice", null),
                "INVALID_MONEY_PRECISION");
        assertError(() -> service.postCreditSale("actor", "customer", "invoice", BigDecimal.ZERO),
                "INVALID_CREDIT_AMOUNT");
        assertError(() -> service.postCreditSale("actor", "missing", "invoice", BigDecimal.TEN),
                "CUSTOMER_NOT_FOUND");

        Customer inactive = customer("Old", false, "0");
        when(balanceRepository.findByCustomerIdForUpdate(inactive.getId()))
                .thenReturn(Optional.of(inactive.getCreditBalance()));
        assertError(() -> service.postCreditSale(
                "actor", inactive.getId(), "invoice-inactive", BigDecimal.TEN),
                "INACTIVE_CUSTOMER");
    }

    private Customer customer(String name, boolean active, String balance) {
        Customer customer = Customer.create(name, "9876543210", null, NOW.minusSeconds(60));
        if (new BigDecimal(balance).signum() > 0) {
            customer.getCreditBalance().addCredit(new BigDecimal(balance).setScale(2), NOW.minusSeconds(30));
        }
        if (!active) {
            customer.update(name, customer.getPhone(), null, false, NOW.minusSeconds(20));
        }
        return customer;
    }

    private KhataRequests.SettlementRequest settlement(
            String amount,
            SettlementMode mode,
            long version) {
        return new KhataRequests.SettlementRequest(
                new BigDecimal(amount), mode, " UPI-1 ", " Partial ", version);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
