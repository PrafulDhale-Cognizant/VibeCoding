package com.simplifiedbilling.purchasing.service.impl;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.service.PurchaseInventoryService;
import com.simplifiedbilling.inventory.service.PurchaseProductSnapshot;
import com.simplifiedbilling.inventory.service.PurchaseReturnInventoryService;
import com.simplifiedbilling.purchasing.domain.Purchase;
import com.simplifiedbilling.purchasing.domain.PurchaseReturn;
import com.simplifiedbilling.purchasing.domain.PurchaseReturnReason;
import com.simplifiedbilling.purchasing.domain.Supplier;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntry;
import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntryType;
import com.simplifiedbilling.purchasing.repository.PurchaseRepository;
import com.simplifiedbilling.purchasing.repository.PurchaseReturnRepository;
import com.simplifiedbilling.purchasing.repository.SupplierAmountAggregate;
import com.simplifiedbilling.purchasing.repository.SupplierLedgerRepository;
import com.simplifiedbilling.purchasing.repository.SupplierPayableBalanceRepository;
import com.simplifiedbilling.purchasing.repository.SupplierRepository;
import com.simplifiedbilling.purchasing.dto.PurchasingRequests;
import com.simplifiedbilling.purchasing.mapper.PurchasingMapper;
import com.simplifiedbilling.purchasing.service.PurchaseNumberAllocator;
import com.simplifiedbilling.purchasing.service.PurchasePricingEngine;
import com.simplifiedbilling.purchasing.service.PurchaseReturnNumberAllocator;
import com.simplifiedbilling.purchasing.service.SupplierPhoneNormalizer;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.dto.StoreDetails;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseReturnAndAnalyticsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");
    private static final String RETURN_KEY = "purchase-return-key-1";

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
                new SupplierPhoneNormalizer(), new PurchasingMapper(), auditWriter, storeService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completesPartialReturnReversesStockAndSplitsPayableFromCredit() {
        Supplier supplier = supplierWithPayable("50.00");
        Purchase purchase = purchase(supplier);
        var source = purchase.getItems().getFirst();
        when(purchaseReturnRepository.findDetailedByIdempotencyKey(RETURN_KEY))
                .thenReturn(Optional.empty());
        when(purchaseRepository.findDetailedByIdForUpdate(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        when(balanceRepository.findBySupplierIdForUpdate(supplier.getId()))
                .thenReturn(Optional.of(supplier.getPayableBalance()));
        when(returnNumberAllocator.next()).thenReturn("PRN-000001");

        var response = service.returnPurchase(
                "actor", purchase.getId(), RETURN_KEY,
                returnRequest(source.getId(), "0.500", LocalDate.of(2026, 8, 2)));

        assertThat(response.returnNumber()).isEqualTo("PRN-000001");
        assertThat(response.totalAmount()).isEqualByComparingTo("59.00");
        assertThat(response.payableReduction()).isEqualByComparingTo("50.00");
        assertThat(response.creditAdded()).isEqualByComparingTo("9.00");
        assertThat(response.supplierPayableAfter()).isZero();
        assertThat(response.supplierCreditAfter()).isEqualByComparingTo("9.00");
        assertThat(source.getReturnedQuantity()).isEqualByComparingTo("0.500");
        verify(returnInventoryService).returnToSupplier(eq("actor"), eq(response.id()), any());

        ArgumentCaptor<PurchaseReturn> savedReturn = ArgumentCaptor.forClass(PurchaseReturn.class);
        verify(purchaseReturnRepository).saveAndFlush(savedReturn.capture());
        ArgumentCaptor<SupplierLedgerEntry> ledger = ArgumentCaptor.forClass(SupplierLedgerEntry.class);
        verify(ledgerRepository).save(ledger.capture());
        assertThat(ledger.getValue().getEntryType()).isEqualTo(SupplierLedgerEntryType.PURCHASE_RETURN);
        assertThat(ledger.getValue().getPurchaseReturn()).isSameAs(savedReturn.getValue());
        assertThat(ledger.getValue().getBalanceAfter()).isZero();
        assertThat(ledger.getValue().getCreditBalanceAfter()).isEqualByComparingTo("9.00");
        verify(auditWriter).write(
                eq("actor"), eq("PURCHASE_RETURN_COMPLETED"),
                eq("PURCHASE_RETURN"), eq(response.id()), any());

        when(purchaseReturnRepository.findDetailedById(response.id()))
                .thenReturn(Optional.of(savedReturn.getValue()));
        assertThat(service.getPurchaseReturn(response.id()).returnNumber()).isEqualTo("PRN-000001");
        when(purchaseReturnRepository.search(
                eq(supplier.getId()), eq(purchase.getId()), any(), any(), eq("%prn%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(savedReturn.getValue())));
        assertThat(service.searchPurchaseReturns(
                "PRN", supplier.getId(), purchase.getId(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 0, 25).content())
                .singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(response.id()));

        when(purchaseReturnRepository.findDetailedByIdempotencyKey(RETURN_KEY))
                .thenReturn(Optional.of(savedReturn.getValue()));
        assertThat(service.returnPurchase(
                "actor", purchase.getId(), RETURN_KEY,
                returnRequest(source.getId(), "0.500", LocalDate.of(2026, 8, 2)))
                .idempotentReplay()).isTrue();
        assertError(() -> service.returnPurchase(
                "actor", "another-purchase", RETURN_KEY,
                returnRequest(source.getId(), "0.500", LocalDate.of(2026, 8, 2))),
                "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void validatesReturnKeyPurchaseDateLinesQuantityAndSearchRange() {
        Purchase purchase = purchase(supplierWithPayable("0"));
        var source = purchase.getItems().getFirst();
        assertError(() -> service.returnPurchase(
                "actor", purchase.getId(), "bad key",
                returnRequest(source.getId(), "1", LocalDate.of(2026, 8, 2))),
                "INVALID_IDEMPOTENCY_KEY");

        when(purchaseReturnRepository.findDetailedByIdempotencyKey(RETURN_KEY))
                .thenReturn(Optional.empty());
        assertError(() -> service.returnPurchase(
                "actor", "missing", RETURN_KEY,
                returnRequest(source.getId(), "1", LocalDate.of(2026, 8, 2))),
                "PURCHASE_NOT_FOUND");

        when(purchaseRepository.findDetailedByIdForUpdate(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        assertError(() -> service.returnPurchase(
                "actor", purchase.getId(), RETURN_KEY,
                returnRequest(source.getId(), "1", LocalDate.of(2026, 7, 31))),
                "INVALID_PURCHASE_RETURN_DATE");

        when(balanceRepository.findBySupplierIdForUpdate(purchase.getSupplier().getId()))
                .thenReturn(Optional.of(purchase.getSupplier().getPayableBalance()));
        assertError(() -> service.returnPurchase(
                "actor", purchase.getId(), RETURN_KEY,
                returnRequest("not-a-line", "1", LocalDate.of(2026, 8, 2))),
                "PURCHASE_RETURN_LINE_NOT_FOUND");
        assertError(() -> service.returnPurchase(
                "actor", purchase.getId(), RETURN_KEY,
                new PurchasingRequests.CreatePurchaseReturnRequest(
                        LocalDate.of(2026, 8, 2), PurchaseReturnReason.OTHER,
                        List.of(
                                new PurchasingRequests.PurchaseReturnItemRequest(source.getId(), BigDecimal.ONE),
                                new PurchasingRequests.PurchaseReturnItemRequest(source.getId(), BigDecimal.ONE)),
                        null)),
                "DUPLICATE_PURCHASE_RETURN_LINE");
        assertError(() -> service.returnPurchase(
                "actor", purchase.getId(), RETURN_KEY,
                returnRequest(source.getId(), "2.001", LocalDate.of(2026, 8, 2))),
                "RETURN_QUANTITY_EXCEEDS_AVAILABLE");
        assertError(() -> service.returnPurchase(
                "actor", purchase.getId(), RETURN_KEY,
                returnRequest(source.getId(), "0.0001", LocalDate.of(2026, 8, 2))),
                "INVALID_QUANTITY_PRECISION");

        assertError(() -> service.getPurchaseReturn("missing"), "PURCHASE_RETURN_NOT_FOUND");
        assertError(() -> service.searchPurchaseReturns(
                null, null, null, LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 1), 0, 25), "INVALID_PURCHASE_RETURN_RANGE");
        assertError(() -> service.searchPurchaseReturns(
                null, null, null, LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2), 0, 25), "PURCHASE_RETURN_RANGE_TOO_LARGE");
    }

    @Test
    void aggregatesSupplierAnalyticsInStoreTimezoneAndValidatesRange() {
        Supplier supplier = supplierWithPayable("80.00");
        SupplierAmountAggregate purchase = amount(supplier.getId(), "200.00");
        SupplierAmountAggregate purchaseReturn = amount(supplier.getId(), "50.00");
        SupplierAmountAggregate payment = amount(supplier.getId(), "70.00");
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 2);
        StoreDetails store = mock(StoreDetails.class);
        when(store.timezone()).thenReturn("Asia/Kolkata");
        when(storeService.getStore()).thenReturn(store);
        when(purchaseRepository.totalBySupplier(from, to)).thenReturn(List.of(purchase));
        when(purchaseReturnRepository.totalBySupplier(from, to)).thenReturn(List.of(purchaseReturn));
        when(ledgerRepository.paymentsBySupplier(any(), any())).thenReturn(List.of(payment));
        when(supplierRepository.findAllDetailed()).thenReturn(List.of(supplier));
        when(balanceRepository.totalOutstanding()).thenReturn(new BigDecimal("80.00"));
        when(balanceRepository.totalCredit()).thenReturn(BigDecimal.ZERO);

        var analytics = service.getSupplierAnalytics(from, to);

        assertThat(analytics.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(analytics.purchaseTotal()).isEqualByComparingTo("200.00");
        assertThat(analytics.returnTotal()).isEqualByComparingTo("50.00");
        assertThat(analytics.netPurchaseTotal()).isEqualByComparingTo("150.00");
        assertThat(analytics.paymentTotal()).isEqualByComparingTo("70.00");
        assertThat(analytics.suppliers()).singleElement().satisfies(row -> {
            assertThat(row.supplierName()).isEqualTo("Fresh");
            assertThat(row.outstandingAmount()).isEqualByComparingTo("80.00");
        });

        assertError(() -> service.getSupplierAnalytics(null, to), "ANALYTICS_DATES_REQUIRED");
        assertError(() -> service.getSupplierAnalytics(to, from), "INVALID_ANALYTICS_RANGE");
        assertError(() -> service.getSupplierAnalytics(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)),
                "ANALYTICS_RANGE_TOO_LARGE");
        when(store.timezone()).thenReturn("Not/A-Timezone");
        assertError(() -> service.getSupplierAnalytics(from, to), "INVALID_STORE_TIMEZONE");
    }

    private Purchase purchase(Supplier supplier) {
        var pricing = new PurchasePricingEngine().calculate(List.of(new PurchaseProductSnapshot(
                "product-1", "Rice", ProductUnit.KILOGRAM, new BigDecimal("2.000"),
                new BigDecimal("118.00"), new BigDecimal("18.00"))), true);
        return Purchase.received(
                "purchase-1", "PUR-000001", "purchase-key-1", supplier, "SUP-1",
                LocalDate.of(2026, 8, 1), pricing, BigDecimal.ZERO.setScale(2),
                null, null, null, "actor", NOW.minusSeconds(60));
    }

    private Supplier supplierWithPayable(String amount) {
        Supplier supplier = Supplier.create(
                "Fresh", "9876543210", null, null, null, NOW.minusSeconds(120));
        if (new BigDecimal(amount).signum() > 0) {
            supplier.getPayableBalance().addPayable(
                    new BigDecimal(amount).setScale(2), NOW.minusSeconds(90));
        }
        return supplier;
    }

    private PurchasingRequests.CreatePurchaseReturnRequest returnRequest(
            String itemId, String quantity, LocalDate date) {
        return new PurchasingRequests.CreatePurchaseReturnRequest(
                date, PurchaseReturnReason.QUALITY_ISSUE,
                List.of(new PurchasingRequests.PurchaseReturnItemRequest(
                        itemId, new BigDecimal(quantity))),
                " Supplier accepted ");
    }

    private SupplierAmountAggregate amount(String supplierId, String value) {
        SupplierAmountAggregate aggregate = mock(SupplierAmountAggregate.class);
        when(aggregate.getSupplierId()).thenReturn(supplierId);
        when(aggregate.getAmount()).thenReturn(new BigDecimal(value));
        return aggregate;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
