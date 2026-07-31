package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PurchaseReturnNumberAllocator {

    private final JdbcTemplate jdbcTemplate;

    public PurchaseReturnNumberAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next() {
        Long nextValue = jdbcTemplate.queryForObject(
                "SELECT next_value FROM purchase_return_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class,
                "PURCHASE_RETURN");
        if (nextValue == null) {
            throw new ApplicationException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PURCHASE_RETURN_SEQUENCE_UNAVAILABLE",
                    "Purchase-return numbering is unavailable.");
        }
        jdbcTemplate.update(
                "UPDATE purchase_return_sequences SET next_value = ? WHERE sequence_name = ?",
                nextValue + 1,
                "PURCHASE_RETURN");
        return "PRN-" + String.format(Locale.ROOT, "%06d", nextValue);
    }
}
