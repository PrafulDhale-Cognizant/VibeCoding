package com.simplifiedbilling.purchasing.service.impl;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.service.PurchaseInventoryService;
import com.simplifiedbilling.inventory.service.PurchaseProductSnapshot;
import com.simplifiedbilling.inventory.service.PurchaseReturnInventoryService;
import com.simplifiedbilling.purchasing.domain.Supplier;
import com.simplifiedbilling.purchasing.domain.SupplierBalanceStatus;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntry;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntryType;
import com.simplifiedbilling.purchasing.domain.SupplierPaymentMode;
import com.simplifiedbilling.purchasing.dto.PurchasingRequests;
import com.simplifiedbilling.purchasing.mapper.PurchasingMapper;
import com.simplifiedbilling.purchasing.repository.PurchaseRepository;
import com.simplifiedbilling.purchasing.repository.PurchaseReturnRepository;
import com.simplifiedbilling.purchasing.repository.SupplierLedgerRepository;
import com.simplifiedbilling.purchasing.repository.SupplierPayableBalanceRepository;
import com.simplifiedbilling.purchasing.repository.SupplierRepository;
import com.simplifiedbilling.purchasing.service.PurchaseNumberAllocator;
import com.simplifiedbilling.purchasing.service.PurchasePricingEngine;
import com.simplifiedbilling.purchasing.service.PurchaseReturnNumberAllocator;
import com.simplifiedbilling.purchasing.service.SupplierPhoneNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.service.StoreService;
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
import java.time.LocalDate;
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
class DefaultPurchasingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");
    private static final String PURCHASE_KEY = "purchase-key-1";
    private static final String PAYMENT_KEY = "supplier-payment-1";

    @Mock private SupplierRepository supplierRepository;
    @Mock private SupplierPayableBalanceRepository balanceRepository;
    @Mock private SupplierLedgerRepository ledgerRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private PurchaseReturnRepository purchaseReturnRepository;
    @Mock private PurchaseInventoryService inventoryService;
    @Mock private PurchaseReturnInventoryService returnInventoryService;
    @Mock private PurchaseNumberAllocator numberAllocator;
    @Mock private PurchaseReturnNumberAllocator returnNumberAllocator;
    @Mock private AuditWriter auditWriter;
    @Mock private StoreService storeService;
    private DefaultPurchasingService service;

    @BeforeEach
    void setUp() {
        service = new DefaultPurchasingService(
                supplierRepository, balanceRepository, ledgerRepository, purchaseRepository,
                purchaseReturnRepository, inventoryService, returnInventoryService,
                new PurchasePricingEngine(), numberAllocator, returnNumberAllocator,
                new SupplierPhoneNormalizer(), new PurchasingMapper(), auditWriter,
                storeService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void searchesCreatesAndRetrievesNormalizedSuppliers() {
        Supplier listed = supplier("Fresh Foods", true, "25.00");
        when(supplierRepository.search(eq("%fresh%"), eq(true), eq("DUE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listed)));

        var page = service.searchSuppliers(" Fresh ", true, SupplierBalanceStatus.DUE, 0, 25);

        assertThat(page.content()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Fresh Foods");
            assertThat(item.outstandingAmount()).isEqualByComparingTo("25.00");
        });
        when(supplierRepository.search(eq(null), eq(null), eq("ALL"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.searchSuppliers(" ", null, null, 0, 10).content()).isEmpty();
        assertError(() -> service.searchSuppliers(null, null, null, -1, 10), "INVALID_PAGE_REQUEST");

        ArgumentCaptor<Supplier> saved = ArgumentCaptor.forClass(Supplier.class);
        var created = service.createSupplier("actor", new PurchasingRequests.CreateSupplierRequest(
                " Fresh Foods ", "+91 98765 43210", " 27abcde1234f1z5 ", " Market ", " Weekly "));
        verify(supplierRepository).saveAndFlush(saved.capture());
        assertThat(created.phone()).isEqualTo("9876543210");
        assertThat(created.gstin()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(created.address()).isEqualTo("Market");
        verify(auditWriter).write(eq("actor"), eq("SUPPLIER_CREATED"), eq("SUPPLIER"), eq(created.id()), any());

        when(supplierRepository.findDetailedById(created.id())).thenReturn(Optional.of(saved.getValue()));
        assertThat(service.getSupplier(created.id()).name()).isEqualTo("Fresh Foods");
        assertError(() -> service.getSupplier("missing"), "SUPPLIER_NOT_FOUND");
    }

    @Test
    void validatesSupplierIdentityAndUpdatesWithVersion() {
        assertError(() -> service.createSupplier("actor", new PurchasingRequests.CreateSupplierRequest(
                " ", "9876543210", null, null, null)), "INVALID_SUPPLIER_NAME");
        assertError(() -> service.createSupplier("actor", new PurchasingRequests.CreateSupplierRequest(
                "Fresh", "9876543210", "invalid", null, null)), "INVALID_SUPPLIER_GSTIN");
        when(supplierRepository.existsByPhone("9876543210")).thenReturn(true);
        assertError(() -> service.createSupplier("actor", new PurchasingRequests.CreateSupplierRequest(
                "Fresh", "9876543210", null, null, null)), "SUPPLIER_PHONE_EXISTS");

        Supplier supplier = supplier("Fresh", true, "0");
        when(supplierRepository.findDetailedById(supplier.getId())).thenReturn(Optional.of(supplier));
        var updated = service.updateSupplier("actor", supplier.getId(),
                new PurchasingRequests.UpdateSupplierRequest(
                        " Fresh Ltd ", "99999 99999", "", " New market ", " ", false, 0));
        assertThat(updated.name()).isEqualTo("Fresh Ltd");
        assertThat(updated.phone()).isEqualTo("9999999999");
        assertThat(updated.gstin()).isNull();
        assertThat(updated.notes()).isNull();
        assertThat(updated.active()).isFalse();
        verify(supplierRepository).flush();

        assertError(() -> service.updateSupplier("actor", supplier.getId(),
                new PurchasingRequests.UpdateSupplierRequest(
                        "Fresh", "9999999999", null, null, null, true, 4)),
                "STALE_SUPPLIER_VERSION");

        Supplier another = supplier("Another", true, "0");
        when(supplierRepository.findDetailedById(another.getId())).thenReturn(Optional.of(another));
        when(supplierRepository.existsByGstinAndIdNot("27ABCDE1234F1Z5", another.getId())).thenReturn(true);
        assertError(() -> service.updateSupplier("actor", another.getId(),
                new PurchasingRequests.UpdateSupplierRequest(
                        "Another", "9999999999", "27ABCDE1234F1Z5", null, null, true, 0)),
                "SUPPLIER_GSTIN_EXISTS");
    }

    @Test
    void returnsSummaryAndPagesSupplierStatement() {
        when(balanceRepository.totalOutstanding()).thenReturn(new BigDecimal("250.5"));
        when(balanceRepository.totalCredit()).thenReturn(new BigDecimal("15.25"));
        when(balanceRepository.countByOutstandingAmountGreaterThan(new BigDecimal("0.00"))).thenReturn(2L);
        when(balanceRepository.countByCreditAmountGreaterThan(new BigDecimal("0.00"))).thenReturn(1L);
        when(supplierRepository.countByActiveTrue()).thenReturn(4L);
        var summary = service.getSummary();
        assertThat(summary.totalOutstanding()).isEqualByComparingTo("250.50");
        assertThat(summary.totalCredit()).isEqualByComparingTo("15.25");
        assertThat(summary.suppliersWithDue()).isEqualTo(2);
        assertThat(summary.suppliersWithCredit()).isEqualTo(1);
        assertThat(summary.activeSuppliers()).isEqualTo(4);
        when(balanceRepository.totalOutstanding()).thenReturn(null);
        when(balanceRepository.totalCredit()).thenReturn(null);
        assertThat(service.getSummary().totalOutstanding()).isZero();
        assertThat(service.getSummary().totalCredit()).isZero();

        Supplier supplier = supplier("Fresh", true, "100");
        SupplierLedgerEntry payment = SupplierLedgerEntry.payment(
                supplier, new BigDecimal("10.00"), new BigDecimal("90.00"),
                SupplierPaymentMode.CASH, PAYMENT_KEY, null, null, "actor", NOW);
        when(supplierRepository.existsById(supplier.getId())).thenReturn(true);
        when(ledgerRepository.findBySupplier_IdOrderByOccurredAtDesc(eq(supplier.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment)));
        assertThat(service.getSupplierStatement(supplier.getId(), 0, 50).content())
                .extracting("entryType").containsExactly(SupplierLedgerEntryType.PAYMENT);
        assertError(() -> service.getSupplierStatement("missing", 0, 50), "SUPPLIER_NOT_FOUND");
    }

    @Test
    void recordsLockedSupplierPaymentAndSupportsReplay() {
        Supplier supplier = supplier("Fresh", true, "100");
        when(ledgerRepository.findByIdempotencyKey(PAYMENT_KEY)).thenReturn(Optional.empty());
        when(balanceRepository.findBySupplierIdForUpdate(supplier.getId()))
                .thenReturn(Optional.of(supplier.getPayableBalance()));

        var response = service.paySupplier(
                "actor", supplier.getId(), PAYMENT_KEY,
                payment("40.00", SupplierPaymentMode.BANK_TRANSFER, 0));

        assertThat(response.balanceAfter()).isEqualByComparingTo("60.00");
        assertThat(response.idempotentReplay()).isFalse();
        ArgumentCaptor<SupplierLedgerEntry> saved = ArgumentCaptor.forClass(SupplierLedgerEntry.class);
        verify(ledgerRepository).save(saved.capture());
        assertThat(saved.getValue().getPaymentReference()).isEqualTo("NEFT-1");
        verify(balanceRepository).flush();

        when(ledgerRepository.findByIdempotencyKey(PAYMENT_KEY)).thenReturn(Optional.of(saved.getValue()));
        assertThat(service.paySupplier(
                "actor", supplier.getId(), PAYMENT_KEY,
                payment("40", SupplierPaymentMode.BANK_TRANSFER, 0)).idempotentReplay()).isTrue();

        Supplier other = supplier("Other", true, "0");
        assertError(() -> service.paySupplier(
                "actor", other.getId(), PAYMENT_KEY,
                payment("10", SupplierPaymentMode.CASH, 0)), "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void validatesSupplierPaymentKeyAmountVersionAndDue() {
        Supplier supplier = supplier("Fresh", true, "100");
        assertError(() -> service.paySupplier(
                "actor", supplier.getId(), "bad key", payment("10", SupplierPaymentMode.CASH, 0)),
                "INVALID_IDEMPOTENCY_KEY");
        when(ledgerRepository.findByIdempotencyKey(PAYMENT_KEY)).thenReturn(Optional.empty());
        assertError(() -> service.paySupplier(
                "actor", supplier.getId(), PAYMENT_KEY, payment("1.001", SupplierPaymentMode.CASH, 0)),
                "INVALID_MONEY_PRECISION");
        assertError(() -> service.paySupplier(
                "actor", supplier.getId(), PAYMENT_KEY, payment("0", SupplierPaymentMode.CASH, 0)),
                "INVALID_SUPPLIER_PAYMENT");
        assertError(() -> service.paySupplier(
                "actor", supplier.getId(), PAYMENT_KEY, payment("10", null, 0)),
                "INVALID_SUPPLIER_PAYMENT");
        when(balanceRepository.findBySupplierIdForUpdate(supplier.getId()))
                .thenReturn(Optional.of(supplier.getPayableBalance()));
        assertError(() -> service.paySupplier(
                "actor", supplier.getId(), PAYMENT_KEY, payment("10", SupplierPaymentMode.CASH, 3)),
                "STALE_SUPPLIER_BALANCE");
        assertError(() -> service.paySupplier(
                "actor", supplier.getId(), PAYMENT_KEY, payment("101", SupplierPaymentMode.CASH, 0)),
                "PAYMENT_EXCEEDS_DUE");
        assertError(() -> service.paySupplier(
                "actor", "missing", PAYMENT_KEY, payment("10", SupplierPaymentMode.CASH, 0)),
                "SUPPLIER_NOT_FOUND");
    }

    @Test
    void atomicallyReceivesPurchaseAddsStockAndSupplierPayable() {
        Supplier supplier = supplier("Fresh", true, "50");
        preparePurchase(supplier, snapshot("118.00"));

        var response = service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                supplier.getId(), "SUP-1", "100.00", SupplierPaymentMode.UPI));

        assertThat(response.purchaseNumber()).isEqualTo("PUR-000001");
        assertThat(response.totalAmount()).isEqualByComparingTo("118.00");
        assertThat(response.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(response.outstandingAdded()).isEqualByComparingTo("18.00");
        assertThat(response.items()).hasSize(1);
        assertThat(supplier.getPayableBalance().getOutstandingAmount()).isEqualByComparingTo("68.00");
        verify(inventoryService).receivePurchase(eq("actor"), eq(response.id()), any());
        ArgumentCaptor<com.simplifiedbilling.purchasing.domain.Purchase> purchase =
                ArgumentCaptor.forClass(com.simplifiedbilling.purchasing.domain.Purchase.class);
        verify(purchaseRepository).saveAndFlush(purchase.capture());
        ArgumentCaptor<SupplierLedgerEntry> ledger = ArgumentCaptor.forClass(SupplierLedgerEntry.class);
        verify(ledgerRepository).save(ledger.capture());
        assertThat(ledger.getValue().getPurchase()).isSameAs(purchase.getValue());
        assertThat(ledger.getValue().getAmount()).isEqualByComparingTo("18.00");
        verify(auditWriter).write(eq("actor"), eq("PURCHASE_RECEIVED"), eq("PURCHASE"), eq(response.id()), any());
    }

    @Test
    void supportsFullyPaidPurchaseAndIdempotentReplay() {
        Supplier supplier = supplier("Fresh", true, "0");
        preparePurchase(supplier, snapshot("118.00"));
        var response = service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                supplier.getId(), null, "118.00", SupplierPaymentMode.CASH));
        assertThat(response.outstandingAdded()).isZero();
        verify(ledgerRepository, never()).save(any());

        when(purchaseRepository.findDetailedByIdempotencyKey(PURCHASE_KEY))
                .thenReturn(Optional.of(capturedPurchase(response, supplier)));
        assertThat(service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                supplier.getId(), null, "118", SupplierPaymentMode.CASH)).idempotentReplay()).isTrue();
    }

    @Test
    void validatesPurchaseSupplierInvoiceAndReceiptPayment() {
        Supplier supplier = supplier("Fresh", true, "0");
        assertError(() -> service.receivePurchase("actor", "bad key", purchaseRequest(
                supplier.getId(), null, "0", null)), "INVALID_IDEMPOTENCY_KEY");
        when(purchaseRepository.findDetailedByIdempotencyKey(PURCHASE_KEY)).thenReturn(Optional.empty());
        when(purchaseRepository.existsBySupplier_IdAndSupplierInvoiceNumber(supplier.getId(), "SUP-1"))
                .thenReturn(true);
        assertError(() -> service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                supplier.getId(), " SUP-1 ", "0", null)), "SUPPLIER_INVOICE_EXISTS");

        when(purchaseRepository.existsBySupplier_IdAndSupplierInvoiceNumber("missing", "SUP-2"))
                .thenReturn(false);
        assertError(() -> service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                "missing", "SUP-2", "0", null)), "SUPPLIER_NOT_FOUND");

        Supplier inactive = supplier("Old", false, "0");
        when(balanceRepository.findBySupplierIdForUpdate(inactive.getId()))
                .thenReturn(Optional.of(inactive.getPayableBalance()));
        assertError(() -> service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                inactive.getId(), null, "0", null)), "INACTIVE_SUPPLIER");

        Supplier active = supplier("Active", true, "0");
        preparePurchaseInputs(active, snapshot("118.00"));
        assertError(() -> service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                active.getId(), null, "119.00", SupplierPaymentMode.CASH)), "INVALID_PURCHASE_PAYMENT");
        assertError(() -> service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                active.getId(), null, "10.00", null)), "PURCHASE_PAYMENT_MODE_REQUIRED");
        assertError(() -> service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                active.getId(), null, "0.00", SupplierPaymentMode.CASH)), "UNEXPECTED_PURCHASE_PAYMENT_MODE");
        assertError(() -> service.receivePurchase("actor", PURCHASE_KEY, purchaseRequestWithPaid(
                active.getId(), new BigDecimal("1.001"), SupplierPaymentMode.CASH)), "INVALID_MONEY_PRECISION");
    }

    @Test
    void getsAndSearchesPurchasesWithRangeValidation() {
        Supplier supplier = supplier("Fresh", true, "0");
        preparePurchase(supplier, snapshot("118.00"));
        var received = service.receivePurchase("actor", PURCHASE_KEY, purchaseRequest(
                supplier.getId(), null, "118", SupplierPaymentMode.CASH));
        var entity = capturedPurchase(received, supplier);
        when(purchaseRepository.findDetailedById("purchase-found")).thenReturn(Optional.of(entity));
        assertThat(service.getPurchase("purchase-found").purchaseNumber()).isEqualTo("PUR-000001");
        assertError(() -> service.getPurchase("missing"), "PURCHASE_NOT_FOUND");

        when(purchaseRepository.search(eq(supplier.getId()), any(), any(), eq("%pur%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        assertThat(service.searchPurchases(
                " PUR ", supplier.getId(), LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2), 0, 25).content()).hasSize(1);
        assertError(() -> service.searchPurchases(null, null,
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), 0, 25),
                "INVALID_PURCHASE_RANGE");
        assertError(() -> service.searchPurchases(null, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), 0, 25),
                "PURCHASE_RANGE_TOO_LARGE");
        assertError(() -> service.searchPurchases(null, null, null, null, 0, 101),
                "INVALID_PAGE_REQUEST");
    }

    private void preparePurchase(Supplier supplier, PurchaseProductSnapshot snapshot) {
        preparePurchaseInputs(supplier, snapshot);
        when(numberAllocator.next()).thenReturn("PUR-000001");
    }

    private void preparePurchaseInputs(Supplier supplier, PurchaseProductSnapshot snapshot) {
        when(purchaseRepository.findDetailedByIdempotencyKey(PURCHASE_KEY)).thenReturn(Optional.empty());
        when(balanceRepository.findBySupplierIdForUpdate(supplier.getId()))
                .thenReturn(Optional.of(supplier.getPayableBalance()));
        when(inventoryService.receivePurchase(eq("actor"), any(), any())).thenReturn(List.of(snapshot));
    }

    private com.simplifiedbilling.purchasing.domain.Purchase capturedPurchase(
            com.simplifiedbilling.purchasing.dto.PurchasingResponses.PurchaseResponse response,
            Supplier supplier) {
        var pricing = new PurchasePricingEngine().calculate(List.of(snapshot("118.00")), true);
        return com.simplifiedbilling.purchasing.domain.Purchase.received(
                response.id(), response.purchaseNumber(), PURCHASE_KEY, supplier, null,
                LocalDate.of(2026, 8, 1), pricing, new BigDecimal("118.00"),
                SupplierPaymentMode.CASH, null, null, "actor", NOW);
    }

    private PurchasingRequests.ReceivePurchaseRequest purchaseRequest(
            String supplierId, String invoice, String paid, SupplierPaymentMode mode) {
        return purchaseRequestWithPaid(supplierId, new BigDecimal(paid), mode, invoice);
    }

    private PurchasingRequests.ReceivePurchaseRequest purchaseRequestWithPaid(
            String supplierId, BigDecimal paid, SupplierPaymentMode mode) {
        return purchaseRequestWithPaid(supplierId, paid, mode, null);
    }

    private PurchasingRequests.ReceivePurchaseRequest purchaseRequestWithPaid(
            String supplierId, BigDecimal paid, SupplierPaymentMode mode, String invoice) {
        return new PurchasingRequests.ReceivePurchaseRequest(
                supplierId, invoice, LocalDate.of(2026, 8, 1), true,
                List.of(new PurchasingRequests.PurchaseItemRequest(
                        "product-1", BigDecimal.ONE, new BigDecimal("118.00"))),
                paid, mode, " UPI-1 ", " Received ");
    }

    private PurchasingRequests.SupplierPaymentRequest payment(
            String amount, SupplierPaymentMode mode, long version) {
        return new PurchasingRequests.SupplierPaymentRequest(
                new BigDecimal(amount), mode, " NEFT-1 ", " Partial ", version);
    }

    private PurchaseProductSnapshot snapshot(String cost) {
        return new PurchaseProductSnapshot(
                "product-1", "Rice", ProductUnit.PIECE, BigDecimal.ONE.setScale(3),
                new BigDecimal(cost), new BigDecimal("18.00"));
    }

    private Supplier supplier(String name, boolean active, String outstanding) {
        Supplier supplier = Supplier.create(name, "9876543210", null, null, null, NOW.minusSeconds(60));
        if (new BigDecimal(outstanding).signum() > 0) {
            supplier.getPayableBalance().addPayable(new BigDecimal(outstanding).setScale(2), NOW.minusSeconds(30));
        }
        if (!active) {
            supplier.update(name, supplier.getPhone(), null, null, null, false, NOW.minusSeconds(20));
        }
        return supplier;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
