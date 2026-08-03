package com.simplifiedbilling.report.service.impl;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.dto.InventoryPage;
import com.simplifiedbilling.inventory.dto.ProductAlertResponse;
import com.simplifiedbilling.inventory.service.ProductService;
import com.simplifiedbilling.khata.dto.KhataResponses;
import com.simplifiedbilling.khata.service.KhataService;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.service.SalesReportSnapshot;
import com.simplifiedbilling.pos.service.SalesReportingService;
import com.simplifiedbilling.pos.service.SalesInsightsService;
import com.simplifiedbilling.report.dto.ReportResponses;
import com.simplifiedbilling.report.mapper.ReportMapper;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.domain.ReceiptWidth;
import com.simplifiedbilling.store.domain.A4InvoiceTemplate;
import com.simplifiedbilling.store.domain.InvoicePrintFormat;
import com.simplifiedbilling.store.domain.ThermalReceiptTemplate;
import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Kolkata");

    @Mock private SalesReportingService salesReportingService;
    @Mock private ProductService productService;
    @Mock private KhataService khataService;
    @Mock private StoreService storeService;
    @Mock private SalesInsightsService insightsService;
    private DefaultReportService service;

    @BeforeEach
    void setUp() {
        service = new DefaultReportService(
                salesReportingService,
                productService,
                khataService,
                storeService,
                insightsService,
                new ReportMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void composesDashboardAcrossModuleServiceBoundaries() {
        when(storeService.getStore()).thenReturn(store("Asia/Kolkata"));
        when(salesReportingService.getSalesReport(any(), any(), eq(STORE_ZONE)))
                .thenReturn(snapshot(LocalDate.of(2026, 7, 31)));
        ProductAlertResponse low = alert("low", StockStatus.LOW_STOCK);
        ProductAlertResponse out = alert("out", StockStatus.OUT_OF_STOCK);
        when(productService.getStockAlerts(StockStatus.LOW_STOCK, 0, 5))
                .thenReturn(page(List.of(low), 4));
        when(productService.getStockAlerts(StockStatus.OUT_OF_STOCK, 0, 5))
                .thenReturn(page(List.of(out), 2));
        when(khataService.getSummary()).thenReturn(
                new KhataResponses.SummaryResponse(money("900"), 3, 10));
        when(insightsService.getTopProducts(any(), any(), eq(8))).thenReturn(List.of());
        when(insightsService.getRecentTransactions(10)).thenReturn(List.of());

        ReportResponses.DashboardResponse dashboard = service.getDashboard();

        assertThat(dashboard.businessDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(dashboard.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(dashboard.shopName()).isEqualTo("My Shop");
        assertThat(dashboard.today().totalSales()).isEqualByComparingTo("100.00");
        assertThat(dashboard.inventory().lowStockCount()).isEqualTo(4);
        assertThat(dashboard.inventory().outOfStockCount()).isEqualTo(2);
        assertThat(dashboard.credit().totalOutstanding()).isEqualByComparingTo("900.00");
        verify(salesReportingService).getSalesReport(
                Instant.parse("2026-07-30T18:30:00Z"),
                Instant.parse("2026-07-31T18:30:00Z"),
                STORE_ZONE);
    }

    @Test
    void buildsInclusiveDateRangeInStoreTimezone() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(storeService.getStore()).thenReturn(store("Asia/Kolkata"));
        when(salesReportingService.getSalesReport(any(), any(), eq(STORE_ZONE)))
                .thenReturn(snapshot(from));

        var report = service.getSalesReport(from, to);

        assertThat(report.from()).isEqualTo(from);
        assertThat(report.to()).isEqualTo(to);
        assertThat(report.dailySales()).hasSize(1);
        assertThat(report.generatedAt()).isEqualTo(NOW);
        verify(salesReportingService).getSalesReport(
                Instant.parse("2026-06-30T18:30:00Z"),
                Instant.parse("2026-07-31T18:30:00Z"),
                STORE_ZONE);
    }

    @Test
    void rejectsMissingReversedAndOversizedDateRanges() {
        assertError(() -> service.getSalesReport(null, LocalDate.of(2026, 7, 1)),
                "REPORT_DATES_REQUIRED");
        assertError(() -> service.getSalesReport(
                        LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1)),
                "INVALID_REPORT_RANGE");
        assertError(() -> service.getSalesReport(
                        LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)),
                "REPORT_RANGE_TOO_LARGE");
    }

    @Test
    void rejectsInvalidConfiguredStoreTimezone() {
        when(storeService.getStore()).thenReturn(store("Mars/Olympus"));

        assertError(service::getDashboard, "INVALID_STORE_TIMEZONE");
    }

    private SalesReportSnapshot snapshot(LocalDate date) {
        return new SalesReportSnapshot(
                NOW.minusSeconds(60), NOW, 2, money("10"), money("100"), money("5"), money("80"),
                money("4"), money("4"), money("0"), money("0"), money("100"),
                money("60"), money("40"), Map.of(PaymentMode.CASH, money("100")),
                List.of(new SalesReportSnapshot.DailySalesSnapshot(
                        date, 2, money("100"), money("60"), money("40"))));
    }

    private ProductAlertResponse alert(String id, StockStatus status) {
        return new ProductAlertResponse(
                id, id + " product", "SKU-" + id, ProductUnit.PIECE,
                money("1"), money("5"), money("4"), status);
    }

    private InventoryPage<ProductAlertResponse> page(
            List<ProductAlertResponse> content, long total) {
        return new InventoryPage<>(content, 0, 5, total, 1, true, true);
    }

    private StoreDetails store(String timezone) {
        return new StoreDetails(
                "My Shop", "Owner", "1 Main Road", null, "Pune", "Maharashtra", "27",
                "411001", "9999999999", null, true, "27ABCDE1234F1Z5", "INR",
                timezone, "INV", 4, ReceiptWidth.MM_80, InvoicePrintFormat.THERMAL,
                A4InvoiceTemplate.MODERN, ThermalReceiptTemplate.CLASSIC, false, 0, NOW, NOW);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
