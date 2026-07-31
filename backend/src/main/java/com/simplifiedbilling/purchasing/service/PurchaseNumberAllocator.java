package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PurchaseNumberAllocator {

    private final JdbcTemplate jdbcTemplate;

    public PurchaseNumberAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next() {
        Long nextValue = jdbcTemplate.queryForObject(
                "SELECT next_value FROM purchase_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class, "PURCHASE");
        if (nextValue == null) {
            throw new ApplicationException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PURCHASE_SEQUENCE_UNAVAILABLE",
                    "Purchase numbering is unavailable.");
        }
        jdbcTemplate.update(
                "UPDATE purchase_sequences SET next_value = ? WHERE sequence_name = ?",
                nextValue + 1, "PURCHASE");
        return "PUR-" + String.format(Locale.ROOT, "%06d", nextValue);
    }
}
