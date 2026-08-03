package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.dto.InvoiceQueryResponses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class InvoiceActivityStore {

    private static final String FIND_SQL = """
            SELECT a.event_type, COALESCE(u.display_name, 'System') AS actor_name, a.occurred_at
            FROM audit_events a
            LEFT JOIN users u ON u.id = a.actor_user_id
            WHERE a.entity_type = 'INVOICE'
              AND a.entity_id = ?
              AND a.event_type IN (
                'SALE_COMPLETED', 'SALE_RETURNED', 'SALE_CANCELLED',
                'INVOICE_THERMAL_REPRINTED', 'INVOICE_A4_PRINTED',
                'INVOICE_PDF_EXPORTED', 'INVOICE_SHARE_COPIED')
            ORDER BY a.occurred_at DESC
            LIMIT 100
            """;

    private final JdbcTemplate jdbcTemplate;

    public InvoiceActivityStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InvoiceQueryResponses.InvoiceActivity> findByInvoiceId(String invoiceId) {
        return jdbcTemplate.query(FIND_SQL, (result, row) -> new InvoiceQueryResponses.InvoiceActivity(
                result.getString("event_type"),
                result.getString("actor_name"),
                result.getTimestamp("occurred_at").toInstant()), invoiceId);
    }
}
