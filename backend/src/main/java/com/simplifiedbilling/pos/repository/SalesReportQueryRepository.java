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
            SELECT COUNT(*) AS bill_count,
                   COALESCE(SUM(subtotal_amount), 0) AS subtotal_amount,
                   COALESCE(SUM(line_discount_amount + bill_discount_amount), 0) AS discount_amount,
                   COALESCE(SUM(taxable_amount), 0) AS taxable_amount,
                   COALESCE(SUM(cgst_amount), 0) AS cgst_amount,
                   COALESCE(SUM(sgst_amount), 0) AS sgst_amount,
                   COALESCE(SUM(igst_amount), 0) AS igst_amount,
                   COALESCE(SUM(round_off_amount), 0) AS round_off_amount,
                   COALESCE(SUM(total_amount), 0) AS total_sales
              FROM invoices
             WHERE status = 'COMPLETED'
               AND completed_at >= ?
               AND completed_at < ?
            """;

    private static final String INVOICE_MARGIN_SQL = """
            SELECT invoice.id,
                   invoice.completed_at,
                   invoice.total_amount,
                   COALESCE(SUM(item.purchase_cost * item.quantity), 0) AS snapshot_cost
              FROM invoices invoice
              LEFT JOIN invoice_items item ON item.invoice_id = invoice.id
             WHERE invoice.status = 'COMPLETED'
               AND invoice.completed_at >= ?
               AND invoice.completed_at < ?
             GROUP BY invoice.id, invoice.completed_at, invoice.total_amount
             ORDER BY invoice.completed_at, invoice.id
            """;

    private static final String PAYMENT_TOTALS_SQL = """
            SELECT payment.payment_mode,
                   COALESCE(SUM(payment.amount), 0) AS payment_total
              FROM payments payment
              JOIN invoices invoice ON invoice.id = payment.invoice_id
             WHERE invoice.status = 'COMPLETED'
               AND invoice.completed_at >= ?
               AND invoice.completed_at < ?
             GROUP BY payment.payment_mode
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
                Timestamp.from(endExclusive)));
    }

    public List<InvoiceMarginRow> findInvoiceMargins(
            Instant startInclusive,
            Instant endExclusive) {
        return jdbcTemplate.query(
                INVOICE_MARGIN_SQL,
                (resultSet, rowNumber) -> new InvoiceMarginRow(
                        resultSet.getString("id"),
                        resultSet.getTimestamp("completed_at").toInstant(),
                        resultSet.getBigDecimal("total_amount"),
                        resultSet.getBigDecimal("snapshot_cost")),
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
                Timestamp.from(endExclusive));
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
            BigDecimal snapshotCost) {
    }

    public record PaymentTotalRow(PaymentMode paymentMode, BigDecimal amount) {
    }
}
