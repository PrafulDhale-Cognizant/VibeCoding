package com.simplifiedbilling.report.mapper;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.domain.StockStatus;
import com.simplifiedbilling.inventory.dto.ProductAlertResponse;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.service.SalesReportSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMapperTest {

    private final ReportMapper mapper = new ReportMapper();

    @Test
    void mapsSalesDailyAndInventoryViews() {
        var daily = new SalesReportSnapshot.DailySalesSnapshot(
                LocalDate.of(2026, 7, 31), 2, money("100"), money("60"), money("40"));
        var sales = new SalesReportSnapshot(
                Instant.EPOCH, Instant.EPOCH.plusSeconds(1), 2, money("100"), money("5"),
                money("84"), money("4"), money("4"), money("3"), money("0"), money("95"),
                money("60"), money("35"), Map.of(PaymentMode.CASH, money("95")), List.of(daily));
        var alert = new ProductAlertResponse(
                "product", "Rice", "SKU-1", ProductUnit.KILOGRAM, money("2"), money("5"),
                money("3"), StockStatus.LOW_STOCK);

        var summary = mapper.toSalesSummary(sales);
        var mappedDaily = mapper.toDailySales(daily);
        var mappedAlert = mapper.toStockAlert(alert);

        assertThat(summary.totalTax()).isEqualByComparingTo("11.00");
        assertThat(summary.grossMargin()).isEqualByComparingTo("35.00");
        assertThat(mappedDaily.businessDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(mappedAlert.name()).isEqualTo("Rice");
        assertThat(mappedAlert.suggestedReorderQuantity()).isEqualByComparingTo("3.00");
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
