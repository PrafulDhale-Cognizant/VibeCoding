package com.simplifiedbilling.pos.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class SaleReturnNumberAllocator {
    private final EntityManager entityManager;
    private final Clock clock;

    public SaleReturnNumberAllocator(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public String next() {
        Number current = (Number) entityManager.createNativeQuery(
                "SELECT next_value FROM sale_return_sequences WHERE sequence_name = 'SALE_RETURN' FOR UPDATE")
                .getSingleResult();
        entityManager.createNativeQuery(
                "UPDATE sale_return_sequences SET next_value = next_value + 1 WHERE sequence_name = 'SALE_RETURN'")
                .executeUpdate();
        int year = LocalDate.now(clock.withZone(ZoneId.systemDefault())).getYear();
        return "SR-" + year + "-" + String.format("%06d", current.longValue());
    }
}
