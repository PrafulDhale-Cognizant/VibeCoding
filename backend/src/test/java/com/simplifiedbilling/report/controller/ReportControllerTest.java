package com.simplifiedbilling.report.controller;

import com.simplifiedbilling.report.dto.ReportResponses;
import com.simplifiedbilling.report.service.ReportService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    @Test
    void delegatesDashboardAndSalesEndpoints() {
        ReportService service = mock(ReportService.class);
        ReportController controller = new ReportController(service);
        ReportResponses.DashboardResponse dashboard = mock(ReportResponses.DashboardResponse.class);
        ReportResponses.SalesReportResponse sales = mock(ReportResponses.SalesReportResponse.class);
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(service.getDashboard()).thenReturn(dashboard);
        when(service.getSalesReport(from, to)).thenReturn(sales);

        assertThat(controller.dashboard()).isSameAs(dashboard);
        assertThat(controller.sales(from, to)).isSameAs(sales);
    }
}
