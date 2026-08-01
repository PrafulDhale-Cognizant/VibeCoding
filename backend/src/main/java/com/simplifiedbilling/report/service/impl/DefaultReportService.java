package com.simplifiedbilling.report.service.impl;

import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.service.ProductService;
import com.simplifiedbilling.khata.service.KhataService;
import com.simplifiedbilling.pos.service.SalesReportSnapshot;
import com.simplifiedbilling.pos.service.SalesReportingService;
import com.simplifiedbilling.pos.service.SalesInsightsService;
import com.simplifiedbilling.report.dto.ReportResponses;
import com.simplifiedbilling.report.mapper.ReportMapper;
import com.simplifiedbilling.report.service.ReportService;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.service.StoreService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class DefaultReportService implements ReportService {

    private static final int ALERT_LIMIT = 5;
    private static final long MAX_RANGE_DAYS = 366;

    private final SalesReportingService salesReportingService;
    private final ProductService productService;
    private final KhataService khataService;
    private final StoreService storeService;
    private final SalesInsightsService insightsService;
    private final ReportMapper mapper;
    private final Clock clock;

    public DefaultReportService(
            SalesReportingService salesReportingService,
            ProductService productService,
            KhataService khataService,
            StoreService storeService,
            SalesInsightsService insightsService,
            ReportMapper mapper,
            Clock clock) {
        this.salesReportingService = salesReportingService;
        this.productService = productService;
        this.khataService = khataService;
        this.storeService = storeService;
        this.insightsService = insightsService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public ReportResponses.DashboardResponse getDashboard() {
        StoreDetails store = storeService.getStore();
        ZoneId zone = requireZone(store.timezone());
        Instant generatedAt = Instant.now(clock);
        LocalDate today = generatedAt.atZone(zone).toLocalDate();
        SalesReportSnapshot sales = loadSales(today, today, zone);
        SalesReportSnapshot month = loadSales(today.withDayOfMonth(1), today, zone);
        SalesReportSnapshot year = loadSales(today.withDayOfYear(1), today, zone);
        SalesReportSnapshot trend = loadSales(today.minusDays(29), today, zone);
        Instant insightStart = today.minusDays(29).atStartOfDay(zone).toInstant();
        Instant insightEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        var lowStock = productService.getStockAlerts(
                StockStatus.LOW_STOCK, 0, ALERT_LIMIT);
        var outOfStock = productService.getStockAlerts(
                StockStatus.OUT_OF_STOCK, 0, ALERT_LIMIT);
        var credit = khataService.getSummary();

        return new ReportResponses.DashboardResponse(
                today,
                zone.getId(),
                store.shopName(),
                mapper.toSalesSummary(sales),
                mapper.toSalesSummary(month),
                mapper.toSalesSummary(year),
                trend.dailySales().stream().map(mapper::toDailySales).toList(),
                insightsService.getTopProducts(insightStart, insightEnd, 8).stream()
                        .map(row -> new ReportResponses.TopProductResponse(
                                row.productId(), row.productName(), row.quantity(), row.netSales())).toList(),
                insightsService.getRecentTransactions(10).stream()
                        .map(row -> new ReportResponses.RecentTransactionResponse(
                                row.id(), row.referenceNumber(), row.type(), row.occurredAt(),
                                row.amount(), row.customerName())).toList(),
                new ReportResponses.InventoryAlertSummaryResponse(
                        lowStock.totalElements(),
                        outOfStock.totalElements(),
                        lowStock.content().stream().map(mapper::toStockAlert).toList(),
                        outOfStock.content().stream().map(mapper::toStockAlert).toList()),
                new ReportResponses.CreditSummaryResponse(
                        credit.totalOutstanding(),
                        credit.customersWithDue(),
                        credit.activeCustomers()),
                generatedAt);
    }

    @Override
    public ReportResponses.SalesReportResponse getSalesReport(LocalDate from, LocalDate to) {
        validateRange(from, to);
        StoreDetails store = storeService.getStore();
        ZoneId zone = requireZone(store.timezone());
        SalesReportSnapshot sales = loadSales(from, to, zone);

        return new ReportResponses.SalesReportResponse(
                from,
                to,
                zone.getId(),
                store.shopName(),
                mapper.toSalesSummary(sales),
                sales.dailySales().stream().map(mapper::toDailySales).toList(),
                Instant.now(clock));
    }

    private SalesReportSnapshot loadSales(LocalDate from, LocalDate to, ZoneId zone) {
        Instant startInclusive = from.atStartOfDay(zone).toInstant();
        Instant endExclusive = to.plusDays(1).atStartOfDay(zone).toInstant();
        return salesReportingService.getSalesReport(startInclusive, endExclusive, zone);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "REPORT_DATES_REQUIRED",
                    "Both report dates are required.");
        }
        if (to.isBefore(from)) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REPORT_RANGE",
                    "The report end date cannot be before the start date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "REPORT_RANGE_TOO_LARGE",
                    "A report can cover at most 366 days.");
        }
    }

    private ZoneId requireZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ApplicationException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_STORE_TIMEZONE",
                    "The configured store timezone is invalid.");
        }
    }
}
