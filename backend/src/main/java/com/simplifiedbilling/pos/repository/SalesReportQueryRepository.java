package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.domain.PaymentMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Repository
public class SalesReportQueryRepository {

    private static final String TOTALS_SQL = """
            SELECT sales.bill_count AS bill_count,
                   sales.subtotal_amount - returned.subtotal_amount AS subtotal_amount,
                   sales.discount_amount - returned.discount_amount AS discount_amount,
                   sales.taxable_amount - returned.taxable_amount AS taxable_amount,
                   sales.cgst_amount - returned.cgst_amount AS cgst_amount,
                   sales.sgst_amount - returned.sgst_amount AS sgst_amount,
                   sales.igst_amount - returned.igst_amount AS igst_amount,
                   sales.round_off_amount - returned.round_off_amount AS round_off_amount,
                   sales.total_sales - returned.total_amount AS total_sales
              FROM (
                    SELECT COUNT(*) AS bill_count,
                           COALESCE(SUM(subtotal_amount), 0) AS subtotal_amount,
                           COALESCE(SUM(line_discount_amount + bill_discount_amount), 0) AS discount_amount,
                           COALESCE(SUM(taxable_amount), 0) AS taxable_amount,
                           COALESCE(SUM(cgst_amount), 0) AS cgst_amount,
                           COALESCE(SUM(sgst_amount), 0) AS sgst_amount,
                           COALESCE(SUM(igst_amount), 0) AS igst_amount,
                           COALESCE(SUM(round_off_amount), 0) AS round_off_amount,
                           COALESCE(SUM(total_amount), 0) AS total_sales
                      FROM invoices WHERE completed_at >= ? AND completed_at < ?
                   ) sales
              CROSS JOIN (
                    SELECT COALESCE(SUM(subtotal_amount), 0) AS subtotal_amount,
                           COALESCE(SUM(discount_amount), 0) AS discount_amount,
                           COALESCE(SUM(taxable_amount), 0) AS taxable_amount,
                           COALESCE(SUM(cgst_amount), 0) AS cgst_amount,
                           COALESCE(SUM(sgst_amount), 0) AS sgst_amount,
                           COALESCE(SUM(igst_amount), 0) AS igst_amount,
                           COALESCE(SUM(total_amount - taxable_amount - cgst_amount - sgst_amount - igst_amount), 0) AS round_off_amount,
                           COALESCE(SUM(total_amount), 0) AS total_amount
                      FROM sale_returns WHERE returned_at >= ? AND returned_at < ?
                   ) returned
            """;

    private static final String INVOICE_MARGIN_SQL = """
            SELECT activity.id, activity.activity_at, activity.total_amount,
                   activity.snapshot_cost, activity.bill_count_delta
              FROM (
                    SELECT invoice.id AS id, invoice.completed_at AS activity_at,
                           invoice.total_amount AS total_amount,
                           COALESCE(SUM(item.purchase_cost * item.quantity), 0) AS snapshot_cost,
                           1 AS bill_count_delta
                      FROM invoices invoice
                      LEFT JOIN invoice_items item ON item.invoice_id = invoice.id
                     WHERE invoice.completed_at >= ? AND invoice.completed_at < ?
                     GROUP BY invoice.id, invoice.completed_at, invoice.total_amount
                    UNION ALL
                    SELECT sale_return.id AS id, sale_return.returned_at AS activity_at,
                           -sale_return.total_amount AS total_amount,
                           -COALESCE(SUM(item.purchase_cost * item.quantity), 0) AS snapshot_cost,
                           0 AS bill_count_delta
                      FROM sale_returns sale_return
                      LEFT JOIN sale_return_items item ON item.sale_return_id = sale_return.id
                     WHERE sale_return.returned_at >= ? AND sale_return.returned_at < ?
                     GROUP BY sale_return.id, sale_return.returned_at, sale_return.total_amount
                   ) activity
             ORDER BY activity.activity_at, activity.id
            """;

    private static final String PAYMENT_TOTALS_SQL = """
            SELECT movement.payment_mode, COALESCE(SUM(movement.amount), 0) AS payment_total
              FROM (
                    SELECT payment.payment_mode, payment.amount
                      FROM payments payment JOIN invoices invoice ON invoice.id = payment.invoice_id
                     WHERE invoice.completed_at >= ? AND invoice.completed_at < ?
                    UNION ALL
                    SELECT refund.refund_mode AS payment_mode, -refund.amount AS amount
                      FROM refund_records refund JOIN sale_returns sale_return ON sale_return.id = refund.sale_return_id
                     WHERE sale_return.returned_at >= ? AND sale_return.returned_at < ?
                   ) movement
             GROUP BY movement.payment_mode
            """;

    private static final String TOP_PRODUCTS_SQL = """
            SELECT movement.product_id, MAX(movement.product_name) AS product_name,
                   COALESCE(SUM(movement.quantity), 0) AS quantity,
                   COALESCE(SUM(movement.amount), 0) AS net_sales
              FROM (
                    SELECT item.product_id, item.product_name, item.quantity, item.line_total AS amount
                      FROM invoice_items item JOIN invoices invoice ON invoice.id = item.invoice_id
                     WHERE invoice.completed_at >= ? AND invoice.completed_at < ?
                    UNION ALL
                    SELECT item.product_id, item.product_name, -item.quantity, -item.line_total
                      FROM sale_return_items item JOIN sale_returns sale_return ON sale_return.id = item.sale_return_id
                     WHERE sale_return.returned_at >= ? AND sale_return.returned_at < ?
                   ) movement
             GROUP BY movement.product_id
            HAVING SUM(movement.quantity) > 0
             ORDER BY quantity DESC, net_sales DESC
             LIMIT ?
            """;

    private static final String RECENT_TRANSACTIONS_SQL = """
            SELECT activity.id, activity.reference_number, activity.activity_type, activity.activity_at,
                   activity.amount, activity.customer_name
              FROM (
                    SELECT invoice.id, invoice.invoice_number AS reference_number, 'SALE' AS activity_type,
                           invoice.completed_at AS activity_at, invoice.total_amount AS amount,
                           MAX(payment.customer_name) AS customer_name
                      FROM invoices invoice LEFT JOIN payments payment ON payment.invoice_id = invoice.id
                     GROUP BY invoice.id, invoice.invoice_number, invoice.completed_at, invoice.total_amount
                    UNION ALL
                    SELECT sale_return.id, sale_return.return_number,
                           CASE WHEN sale_return.return_type = 'CANCELLATION' THEN 'CANCELLATION' ELSE 'RETURN' END,
                           sale_return.returned_at, -sale_return.total_amount, MAX(payment.customer_name)
                      FROM sale_returns sale_return
                      JOIN invoices invoice ON invoice.id = sale_return.invoice_id
                      LEFT JOIN payments payment ON payment.invoice_id = invoice.id
                     GROUP BY sale_return.id, sale_return.return_number, sale_return.return_type,
                              sale_return.returned_at, sale_return.total_amount
                   ) activity
             ORDER BY activity.activity_at DESC, activity.id DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SalesReportQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FinancialTotals findFinancialTotals(Instant startInclusive, Instant endExclusive) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                TOTALS_SQL,
                (resultSet, rowNumber) -> new FinancialTotals(
                        resultSet.getLong("bill_count"),
                        resultSet.getBigDecimal("subtotal_amount"),
                        resultSet.getBigDecimal("discount_amount"),
                        resultSet.getBigDecimal("taxable_amount"),
                        resultSet.getBigDecimal("cgst_amount"),
                        resultSet.getBigDecimal("sgst_amount"),
                        resultSet.getBigDecimal("igst_amount"),
                        resultSet.getBigDecimal("round_off_amount"),
                        resultSet.getBigDecimal("total_sales")),
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive),
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive)));
    }

    public List<InvoiceMarginRow> findInvoiceMargins(
            Instant startInclusive,
            Instant endExclusive) {
        return jdbcTemplate.query(
                INVOICE_MARGIN_SQL,
                (resultSet, rowNumber) -> new InvoiceMarginRow(
                        resultSet.getString("id"),
                        resultSet.getTimestamp("activity_at").toInstant(),
                        resultSet.getBigDecimal("total_amount"),
                        resultSet.getBigDecimal("snapshot_cost"),
                        resultSet.getLong("bill_count_delta")),
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive),
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive));
    }

    public List<PaymentTotalRow> findPaymentTotals(
            Instant startInclusive,
            Instant endExclusive) {
        return jdbcTemplate.query(
                PAYMENT_TOTALS_SQL,
                (resultSet, rowNumber) -> new PaymentTotalRow(
                        PaymentMode.valueOf(resultSet.getString("payment_mode")),
                        resultSet.getBigDecimal("payment_total")),
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive),
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive));
    }

    public List<TopProductRow> findTopProducts(Instant startInclusive, Instant endExclusive, int limit) {
        return jdbcTemplate.query(TOP_PRODUCTS_SQL,
                (resultSet, rowNumber) -> new TopProductRow(
                        resultSet.getString("product_id"), resultSet.getString("product_name"),
                        resultSet.getBigDecimal("quantity"), resultSet.getBigDecimal("net_sales")),
                Timestamp.from(startInclusive), Timestamp.from(endExclusive),
                Timestamp.from(startInclusive), Timestamp.from(endExclusive), limit);
    }

    public List<RecentTransactionRow> findRecentTransactions(int limit) {
        return jdbcTemplate.query(RECENT_TRANSACTIONS_SQL,
                (resultSet, rowNumber) -> new RecentTransactionRow(
                        resultSet.getString("id"), resultSet.getString("reference_number"),
                        resultSet.getString("activity_type"), resultSet.getTimestamp("activity_at").toInstant(),
                        resultSet.getBigDecimal("amount"), resultSet.getString("customer_name")), limit);
    }

    public record FinancialTotals(
            long billCount,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal roundOffAmount,
            BigDecimal totalSales) {
    }

    public record InvoiceMarginRow(
            String invoiceId,
            Instant completedAt,
            BigDecimal totalSales,
            BigDecimal snapshotCost,
            long billCountDelta) {
    }

    public record PaymentTotalRow(PaymentMode paymentMode, BigDecimal amount) {
    }

    public record TopProductRow(String productId, String productName, BigDecimal quantity, BigDecimal netSales) { }

    public record RecentTransactionRow(
            String id, String referenceNumber, String type, Instant occurredAt,
            BigDecimal amount, String customerName) { }
}
