package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class InvoiceNumberAllocator {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceNumberAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next(String requestedPrefix) {
        Long nextValue = jdbcTemplate.queryForObject(
                "SELECT next_value FROM invoice_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class,
                "INVOICE");
        if (nextValue == null) {
            throw new ApplicationException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVOICE_SEQUENCE_UNAVAILABLE",
                    "Invoice numbering is unavailable.");
        }
        jdbcTemplate.update(
                "UPDATE invoice_sequences SET next_value = ? WHERE sequence_name = ?",
                nextValue + 1,
                "INVOICE");
        String prefix = normalizePrefix(requestedPrefix);
        return prefix + "-" + String.format(Locale.ROOT, "%06d", nextValue);
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "INV";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
        return normalized.isBlank() ? "INV" : normalized;
    }
}
