package com.simplifiedbilling.report.service;

import com.simplifiedbilling.report.dto.ReportResponses;

import java.time.LocalDate;

public interface ReportService {

    ReportResponses.DashboardResponse getDashboard();

    ReportResponses.SalesReportResponse getSalesReport(LocalDate from, LocalDate to);
}
