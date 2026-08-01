package com.simplifiedbilling.report.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.pos.domain.PaymentMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ReportResponses {

    private ReportResponses() {
    }

    public record DashboardResponse(
            LocalDate businessDate,
            String timezone,
            String shopName,
            SalesSummaryResponse today,
            SalesSummaryResponse monthToDate,
            SalesSummaryResponse yearToDate,
            List<DailySalesResponse> revenueTrend,
            List<TopProductResponse> topSellingProducts,
            List<RecentTransactionResponse> recentTransactions,
            InventoryAlertSummaryResponse inventory,
            CreditSummaryResponse credit,
            Instant generatedAt) {
        public DashboardResponse {
            revenueTrend = List.copyOf(revenueTrend);
            topSellingProducts = List.copyOf(topSellingProducts);
            recentTransactions = List.copyOf(recentTransactions);
        }
    }

    public record TopProductResponse(
            String productId, String productName, BigDecimal quantity, BigDecimal netSales) { }

    public record RecentTransactionResponse(
            String id, String referenceNumber, String type, Instant occurredAt,
            BigDecimal amount, String customerName) { }

    public record SalesReportResponse(
            LocalDate from,
            LocalDate to,
            String timezone,
            String shopName,
            SalesSummaryResponse summary,
            List<DailySalesResponse> dailySales,
            Instant generatedAt) {

        public SalesReportResponse {
            dailySales = List.copyOf(dailySales);
        }
    }

    public record SalesSummaryResponse(
            long billCount,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal totalTax,
            BigDecimal roundOffAmount,
            BigDecimal totalSales,
            BigDecimal snapshotCost,
            BigDecimal grossMargin,
            Map<PaymentMode, BigDecimal> paymentTotals) {

        public SalesSummaryResponse {
            paymentTotals = Map.copyOf(paymentTotals);
        }
    }

    public record DailySalesResponse(
            LocalDate businessDate,
            long billCount,
            BigDecimal totalSales,
            BigDecimal snapshotCost,
            BigDecimal grossMargin) {
    }

    public record InventoryAlertSummaryResponse(
            long lowStockCount,
            long outOfStockCount,
            List<StockAlertResponse> lowStockItems,
            List<StockAlertResponse> outOfStockItems) {

        public InventoryAlertSummaryResponse {
            lowStockItems = List.copyOf(lowStockItems);
            outOfStockItems = List.copyOf(outOfStockItems);
        }
    }

    public record StockAlertResponse(
            String productId,
            String name,
            String sku,
            ProductUnit unit,
            BigDecimal stockQuantity,
            BigDecimal minimumStockLevel,
            BigDecimal suggestedReorderQuantity) {
    }

    public record CreditSummaryResponse(
            BigDecimal totalOutstanding,
            long customersWithDue,
            long activeCustomers) {
    }
}
