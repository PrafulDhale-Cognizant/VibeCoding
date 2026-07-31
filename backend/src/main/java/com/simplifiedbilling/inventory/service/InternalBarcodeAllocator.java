package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.repository.ProductBarcodeRepository;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InternalBarcodeAllocator {

    static final long MAX_SEQUENCE = 9_999_999_999L;
    private static final String SEQUENCE_NAME = "PRODUCT";

    private final JdbcTemplate jdbcTemplate;
    private final ProductBarcodeRepository barcodeRepository;

    public InternalBarcodeAllocator(
            JdbcTemplate jdbcTemplate,
            ProductBarcodeRepository barcodeRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.barcodeRepository = barcodeRepository;
    }

    @Transactional
    public String allocate() {
        for (int attempt = 0; attempt < 100; attempt++) {
            Long sequence = jdbcTemplate.queryForObject(
                    "SELECT next_value FROM internal_barcode_sequences WHERE sequence_name = ? FOR UPDATE",
                    Long.class,
                    SEQUENCE_NAME);
            if (sequence == null || sequence < 1 || sequence > MAX_SEQUENCE) {
                throw exhausted();
            }
            jdbcTemplate.update(
                    "UPDATE internal_barcode_sequences SET next_value = ? WHERE sequence_name = ?",
                    sequence + 1,
                    SEQUENCE_NAME);

            String barcode = toEan13(sequence);
            if (!barcodeRepository.existsByValue(barcode)) {
                return barcode;
            }
        }
        throw exhausted();
    }

    static String toEan13(long sequence) {
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("Internal barcode sequence is out of range.");
        }
        String body = "20" + String.format(java.util.Locale.ROOT, "%010d", sequence);
        int weightedSum = 0;
        for (int index = 0; index < body.length(); index++) {
            int digit = body.charAt(index) - '0';
            weightedSum += index % 2 == 0 ? digit : digit * 3;
        }
        int checkDigit = (10 - (weightedSum % 10)) % 10;
        return body + checkDigit;
    }

    private ApplicationException exhausted() {
        return new ApplicationException(
                HttpStatus.CONFLICT,
                "BARCODE_SEQUENCE_EXHAUSTED",
                "No internal product barcode is currently available.");
    }
}
