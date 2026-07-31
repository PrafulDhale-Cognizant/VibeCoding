package com.simplifiedbilling.report.controller;

import com.simplifiedbilling.report.dto.ReportResponses;
import com.simplifiedbilling.report.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'VIEWER')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public ReportResponses.DashboardResponse dashboard() {
        return reportService.getDashboard();
    }

    @GetMapping("/sales")
    public ReportResponses.SalesReportResponse sales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.getSalesReport(from, to);
    }
}
