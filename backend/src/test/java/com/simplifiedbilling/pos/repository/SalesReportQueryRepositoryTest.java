package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.domain.PaymentMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SalesReportQueryRepositoryTest {

    private static final Instant START = Instant.parse("2026-07-30T18:30:00Z");
    private static final Instant END = Instant.parse("2026-08-01T18:30:00Z");

    private JdbcTemplate jdbc;
    private SalesReportQueryRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build());
        repository = new SalesReportQueryRepository(jdbc);
        jdbc.execute("""
                CREATE TABLE invoices (
                    id VARCHAR(36) PRIMARY KEY,
                    status VARCHAR(16) NOT NULL,
                    subtotal_amount DECIMAL(19,2) NOT NULL,
                    line_discount_amount DECIMAL(19,2) NOT NULL,
                    bill_discount_amount DECIMAL(19,2) NOT NULL,
                    taxable_amount DECIMAL(19,2) NOT NULL,
                    cgst_amount DECIMAL(19,2) NOT NULL,
                    sgst_amount DECIMAL(19,2) NOT NULL,
                    igst_amount DECIMAL(19,2) NOT NULL,
                    round_off_amount DECIMAL(19,2) NOT NULL,
                    total_amount DECIMAL(19,2) NOT NULL,
                    completed_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE invoice_items (
                    id VARCHAR(36) PRIMARY KEY,
                    invoice_id VARCHAR(36) NOT NULL,
                    purchase_cost DECIMAL(19,2) NOT NULL,
                    quantity DECIMAL(19,3) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE payments (
                    id VARCHAR(36) PRIMARY KEY,
                    invoice_id VARCHAR(36) NOT NULL,
                    payment_mode VARCHAR(16) NOT NULL,
                    amount DECIMAL(19,2) NOT NULL
                )
                """);
    }

    @Test
    void returnsFinancialMarginAndPaymentRowsForCompletedInvoicesInRange() {
        insertInvoice("one", "COMPLETED", "120.00", "5.00", "5.00", "100.00",
                "9.00", "9.00", "0.00", "0.00", "118.00", "2026-07-31T05:00:00Z");
        insertInvoice("two", "CANCELLED", "50.00", "0.00", "0.00", "50.00",
                "0.00", "0.00", "0.00", "0.00", "50.00", "2026-07-31T06:00:00Z");
        insertInvoice("old", "COMPLETED", "20.00", "0.00", "0.00", "20.00",
                "0.00", "0.00", "0.00", "0.00", "20.00", "2026-07-29T06:00:00Z");
        jdbc.update("INSERT INTO invoice_items VALUES (?, ?, ?, ?)", "item-1", "one", 40, 2);
        jdbc.update("INSERT INTO payments VALUES (?, ?, ?, ?)", "payment-1", "one", "CASH", 80);
        jdbc.update("INSERT INTO payments VALUES (?, ?, ?, ?)", "payment-2", "one", "UDHAAR", 38);

        var totals = repository.findFinancialTotals(START, END);
        var margins = repository.findInvoiceMargins(START, END);
        var payments = repository.findPaymentTotals(START, END);

        assertThat(totals.billCount()).isEqualTo(1);
        assertThat(totals.subtotalAmount()).isEqualByComparingTo("120.00");
        assertThat(totals.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(totals.taxableAmount()).isEqualByComparingTo("100.00");
        assertThat(totals.cgstAmount()).isEqualByComparingTo("9.00");
        assertThat(totals.sgstAmount()).isEqualByComparingTo("9.00");
        assertThat(totals.igstAmount()).isEqualByComparingTo("0.00");
        assertThat(totals.roundOffAmount()).isEqualByComparingTo("0.00");
        assertThat(totals.totalSales()).isEqualByComparingTo("118.00");
        assertThat(margins).singleElement().satisfies(row -> {
            assertThat(row.invoiceId()).isEqualTo("one");
            assertThat(row.completedAt()).isEqualTo(Instant.parse("2026-07-31T05:00:00Z"));
            assertThat(row.totalSales()).isEqualByComparingTo("118.00");
            assertThat(row.snapshotCost()).isEqualByComparingTo("80.00000");
        });
        assertThat(payments).extracting(SalesReportQueryRepository.PaymentTotalRow::paymentMode)
                .containsExactlyInAnyOrder(PaymentMode.CASH, PaymentMode.UDHAAR);
    }

    @Test
    void returnsZeroTotalsAndEmptyRowsWhenThereAreNoSales() {
        var totals = repository.findFinancialTotals(START, END);

        assertThat(totals.billCount()).isZero();
        assertThat(totals.totalSales()).isEqualByComparingTo("0");
        assertThat(repository.findInvoiceMargins(START, END)).isEmpty();
        assertThat(repository.findPaymentTotals(START, END)).isEmpty();
    }

    private void insertInvoice(
            String id, String status, String subtotal, String lineDiscount, String billDiscount,
            String taxable, String cgst, String sgst, String igst, String roundOff,
            String total, String completedAt) {
        jdbc.update("""
                        INSERT INTO invoices VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, status, subtotal, lineDiscount, billDiscount, taxable, cgst, sgst, igst,
                roundOff, total, Timestamp.from(Instant.parse(completedAt)));
    }
}
