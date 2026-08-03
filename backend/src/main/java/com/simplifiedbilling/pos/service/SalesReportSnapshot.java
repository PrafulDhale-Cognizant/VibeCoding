package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.pos.domain.PaymentMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Immutable reporting view owned by the POS module. Reporting consumers never
 * need direct access to POS persistence entities or repositories.
 */
public record SalesReportSnapshot(
        Instant startInclusive,
        Instant endExclusive,
        long billCount,
        BigDecimal returnAmount,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal roundOffAmount,
        BigDecimal totalSales,
        BigDecimal snapshotCost,
        BigDecimal grossMargin,
        Map<PaymentMode, BigDecimal> paymentTotals,
        List<DailySalesSnapshot> dailySales) {

    public SalesReportSnapshot {
        paymentTotals = Map.copyOf(paymentTotals);
        dailySales = List.copyOf(dailySales);
    }

    public record DailySalesSnapshot(
            LocalDate businessDate,
            long billCount,
            BigDecimal totalSales,
            BigDecimal snapshotCost,
            BigDecimal grossMargin) {
    }
}
