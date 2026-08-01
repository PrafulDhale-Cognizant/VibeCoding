package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.repository.SalesReportQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSalesReportingServiceTest {

    private static final Instant START = Instant.parse("2026-07-30T18:30:00Z");
    private static final Instant END = Instant.parse("2026-08-01T18:30:00Z");

    @Mock private SalesReportQueryRepository repository;
    private DefaultSalesReportingService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSalesReportingService(repository);
    }

    @Test
    void buildsDailyMarginAndCompletePaymentBreakdown() {
        when(repository.findFinancialTotals(START, END)).thenReturn(
                new SalesReportQueryRepository.FinancialTotals(
                        3, money("350"), money("15"), money("300"), money("9"),
                        money("9"), money("12"), money("0"), money("330")));
        when(repository.findInvoiceMargins(START, END)).thenReturn(List.of(
                margin("one", "2026-07-31T04:00:00Z", "100", "60"),
                margin("two", "2026-07-31T16:00:00Z", "80", "50"),
                margin("three", "2026-08-01T04:00:00Z", "150", "90")));
        when(repository.findPaymentTotals(START, END)).thenReturn(List.of(
                new SalesReportQueryRepository.PaymentTotalRow(PaymentMode.CASH, money("200")),
                new SalesReportQueryRepository.PaymentTotalRow(PaymentMode.UPI, money("130"))));

        var report = service.getSalesReport(START, END, ZoneId.of("Asia/Kolkata"));

        assertThat(report.billCount()).isEqualTo(3);
        assertThat(report.snapshotCost()).isEqualByComparingTo("200.00");
        assertThat(report.grossMargin()).isEqualByComparingTo("130.00");
        assertThat(report.paymentTotals()).containsEntry(PaymentMode.CASH, money("200"))
                .containsEntry(PaymentMode.UPI, money("130"))
                .containsEntry(PaymentMode.CARD, money("0"))
                .containsEntry(PaymentMode.UDHAAR, money("0"));
        assertThat(report.dailySales()).hasSize(2);
        assertThat(report.dailySales().getFirst().billCount()).isEqualTo(2);
        assertThat(report.dailySales().getFirst().grossMargin()).isEqualByComparingTo("70.00");
        assertThat(report.dailySales().get(1).totalSales()).isEqualByComparingTo("150.00");
        verify(repository).findPaymentTotals(START, END);
    }

    @Test
    void rejectsEmptyOrReversedIntervals() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getSalesReport(END, END, ZoneId.of("UTC")))
                .withMessageContaining("start");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getSalesReport(END, START, ZoneId.of("UTC")));
    }

    private SalesReportQueryRepository.InvoiceMarginRow margin(
            String id, String at, String sales, String cost) {
        return new SalesReportQueryRepository.InvoiceMarginRow(
                id, Instant.parse(at), money(sales), money(cost), 1);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
