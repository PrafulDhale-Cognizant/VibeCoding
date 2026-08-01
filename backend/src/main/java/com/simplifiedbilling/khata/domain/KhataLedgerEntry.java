package com.simplifiedbilling.khata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "khata_ledger_entries")
public class KhataLedgerEntry implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_type", length = 24, nullable = false, updatable = false)
    private KhataEntryType entryType;

    @Column(precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal balanceAfter;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "invoice_id", length = 36, updatable = false)
    private String invoiceId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sale_return_id", length = 36, unique = true, updatable = false)
    private String saleReturnId;

    @Column(name = "idempotency_key", length = 80, unique = true, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_mode", length = 16, updatable = false)
    private SettlementMode paymentMode;

    @Column(name = "payment_reference", length = 100, updatable = false)
    private String paymentReference;

    @Column(length = 500, updatable = false)
    private String notes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_user_id", length = 36, nullable = false, updatable = false)
    private String actorUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Transient
    private boolean newEntity = true;

    protected KhataLedgerEntry() {
    }

    public static KhataLedgerEntry creditSale(
            Customer customer,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String invoiceId,
            String actorUserId,
            Instant now) {
        return create(customer, KhataEntryType.CREDIT_SALE, amount, balanceAfter,
                invoiceId, null, null, null, null, "Udhaar sale", actorUserId, now);
    }

    public static KhataLedgerEntry settlement(
            Customer customer,
            BigDecimal amount,
            BigDecimal balanceAfter,
            SettlementMode paymentMode,
            String idempotencyKey,
            String paymentReference,
            String notes,
            String actorUserId,
            Instant now) {
        return create(customer, KhataEntryType.SETTLEMENT, amount, balanceAfter,
                null, null, idempotencyKey, paymentMode, paymentReference, notes, actorUserId, now);
    }

    public static KhataLedgerEntry saleReversal(
            Customer customer, BigDecimal amount, BigDecimal balanceAfter, String invoiceId,
            String saleReturnId, boolean cancellation, String actorUserId, Instant now) {
        return create(customer,
                cancellation ? KhataEntryType.CANCELLATION : KhataEntryType.SALE_RETURN,
                amount, balanceAfter, invoiceId, saleReturnId, null, null, null,
                cancellation ? "Invoice cancellation" : "Customer return", actorUserId, now);
    }

    private static KhataLedgerEntry create(
            Customer customer,
            KhataEntryType entryType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String invoiceId,
            String saleReturnId,
            String idempotencyKey,
            SettlementMode paymentMode,
            String paymentReference,
            String notes,
            String actorUserId,
            Instant now) {
        KhataLedgerEntry entry = new KhataLedgerEntry();
        entry.id = UUID.randomUUID().toString();
        entry.customer = customer;
        entry.entryType = entryType;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.invoiceId = invoiceId;
        entry.saleReturnId = saleReturnId;
        entry.idempotencyKey = idempotencyKey;
        entry.paymentMode = paymentMode;
        entry.paymentReference = paymentReference;
        entry.notes = notes;
        entry.actorUserId = actorUserId;
        entry.occurredAt = now;
        return entry;
    }

    @Override
    public String getId() { return id; }
    @Override
    public boolean isNew() { return newEntity; }
    @PostLoad
    @PostPersist
    void markNotNew() { newEntity = false; }

    public Customer getCustomer() { return customer; }
    public KhataEntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getInvoiceId() { return invoiceId; }
    public String getSaleReturnId() { return saleReturnId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public SettlementMode getPaymentMode() { return paymentMode; }
    public String getPaymentReference() { return paymentReference; }
    public String getNotes() { return notes; }
    public String getActorUserId() { return actorUserId; }
    public Instant getOccurredAt() { return occurredAt; }
}
