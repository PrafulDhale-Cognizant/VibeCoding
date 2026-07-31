package com.simplifiedbilling.report.mapper;

import com.simplifiedbilling.inventory.dto.ProductAlertResponse;
import com.simplifiedbilling.pos.service.SalesReportSnapshot;
import com.simplifiedbilling.report.dto.ReportResponses;
import org.springframework.stereotype.Component;

@Component
public class ReportMapper {

    public ReportResponses.SalesSummaryResponse toSalesSummary(SalesReportSnapshot snapshot) {
        return new ReportResponses.SalesSummaryResponse(
                snapshot.billCount(),
                snapshot.subtotalAmount(),
                snapshot.discountAmount(),
                snapshot.taxableAmount(),
                snapshot.cgstAmount(),
                snapshot.sgstAmount(),
                snapshot.igstAmount(),
                snapshot.cgstAmount().add(snapshot.sgstAmount()).add(snapshot.igstAmount()),
                snapshot.roundOffAmount(),
                snapshot.totalSales(),
                snapshot.snapshotCost(),
                snapshot.grossMargin(),
                snapshot.paymentTotals());
    }

    public ReportResponses.DailySalesResponse toDailySales(
            SalesReportSnapshot.DailySalesSnapshot snapshot) {
        return new ReportResponses.DailySalesResponse(
                snapshot.businessDate(),
                snapshot.billCount(),
                snapshot.totalSales(),
                snapshot.snapshotCost(),
                snapshot.grossMargin());
    }

    public ReportResponses.StockAlertResponse toStockAlert(ProductAlertResponse alert) {
        return new ReportResponses.StockAlertResponse(
                alert.productId(),
                alert.name(),
                alert.sku(),
                alert.unit(),
                alert.stockQuantity(),
                alert.minimumStockLevel(),
                alert.suggestedReorderQuantity());
    }
}
