package com.simplifiedbilling.pos.dto;

import com.simplifiedbilling.pos.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InvoiceQueryResponses {
    private InvoiceQueryResponses() { }

    public record InvoiceSummary(
            String id,
            String invoiceNumber,
            InvoiceStatus status,
            Instant completedAt,
            BigDecimal totalAmount,
            BigDecimal returnableTotal,
            String customerId,
            String customerName,
            String customerPhone) { }

    public record InvoicePage(
            List<InvoiceSummary> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public InvoicePage { content = List.copyOf(content); }
    }

    public record InvoiceActivity(
            String eventType,
            String actorName,
            Instant occurredAt) { }
}
