package com.simplifiedbilling.khata.dto;

import com.simplifiedbilling.khata.domain.KhataEntryType;
import com.simplifiedbilling.khata.domain.SettlementMode;

import java.math.BigDecimal;
import java.time.Instant;

public final class KhataResponses {

    private KhataResponses() {
    }

    public record CustomerResponse(
            String id,
            String name,
            String phone,
            String notes,
            boolean active,
            BigDecimal outstandingAmount,
            long version,
            long balanceVersion,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record LedgerEntryResponse(
            String id,
            String customerId,
            KhataEntryType entryType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String invoiceId,
            SettlementMode paymentMode,
            String paymentReference,
            String notes,
            String actorUserId,
            Instant occurredAt) {
    }

    public record SettlementResponse(
            String entryId,
            String customerId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            SettlementMode paymentMode,
            Instant occurredAt,
            boolean idempotentReplay) {
    }

    public record SummaryResponse(
            BigDecimal totalOutstanding,
            long customersWithDue,
            long activeCustomers) {
    }
}
