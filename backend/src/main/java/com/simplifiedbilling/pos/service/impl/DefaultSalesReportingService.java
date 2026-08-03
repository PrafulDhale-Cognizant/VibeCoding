package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.repository.SalesReportQueryRepository;
import com.simplifiedbilling.pos.repository.SalesReportQueryRepository.InvoiceMarginRow;
import com.simplifiedbilling.pos.service.SalesReportSnapshot;
import com.simplifiedbilling.pos.service.SalesReportingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class DefaultSalesReportingService implements SalesReportingService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final SalesReportQueryRepository reportRepository;

    public DefaultSalesReportingService(SalesReportQueryRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SalesReportSnapshot getSalesReport(
            Instant startInclusive,
            Instant endExclusive,
            ZoneId businessZone) {
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("The report start must be before its end.");
        }

        var totals = reportRepository.findFinancialTotals(startInclusive, endExclusive);
        List<InvoiceMarginRow> invoices = reportRepository.findInvoiceMargins(
                startInclusive, endExclusive);

        Map<PaymentMode, BigDecimal> payments = new EnumMap<>(PaymentMode.class);
        for (PaymentMode mode : PaymentMode.values()) {
            payments.put(mode, ZERO);
        }
        reportRepository.findPaymentTotals(startInclusive, endExclusive)
                .forEach(row -> payments.put(row.paymentMode(), row.amount()));

        BigDecimal snapshotCost = invoices.stream()
                .map(InvoiceMarginRow::snapshotCost)
                .reduce(ZERO, BigDecimal::add);

        Map<LocalDate, DailyAccumulator> daily = new TreeMap<>();
        invoices.forEach(invoice -> {
            LocalDate date = invoice.completedAt().atZone(businessZone).toLocalDate();
            daily.computeIfAbsent(date, ignored -> new DailyAccumulator()).add(invoice);
        });

        return new SalesReportSnapshot(
                startInclusive,
                endExclusive,
                totals.billCount(),
                totals.returnAmount(),
                totals.subtotalAmount(),
                totals.discountAmount(),
                totals.taxableAmount(),
                totals.cgstAmount(),
                totals.sgstAmount(),
                totals.igstAmount(),
                totals.roundOffAmount(),
                totals.totalSales(),
                snapshotCost,
                totals.totalSales().subtract(snapshotCost),
                payments,
                daily.entrySet().stream()
                        .map(entry -> entry.getValue().toSnapshot(entry.getKey()))
                        .toList());
    }

    private static final class DailyAccumulator {

        private long billCount;
        private BigDecimal sales = ZERO;
        private BigDecimal cost = ZERO;

        void add(InvoiceMarginRow invoice) {
            billCount += invoice.billCountDelta();
            sales = sales.add(invoice.totalSales());
            cost = cost.add(invoice.snapshotCost());
        }

        SalesReportSnapshot.DailySalesSnapshot toSnapshot(LocalDate date) {
            return new SalesReportSnapshot.DailySalesSnapshot(
                    date, billCount, sales, cost, sales.subtract(cost));
        }
    }
}
