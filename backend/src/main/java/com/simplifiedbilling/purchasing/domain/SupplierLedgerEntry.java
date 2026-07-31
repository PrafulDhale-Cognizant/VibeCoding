package com.simplifiedbilling.purchasing.domain;

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
@Table(name = "supplier_ledger_entries")
public class SupplierLedgerEntry implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_type", length = 24, nullable = false, updatable = false)
    private SupplierLedgerEntryType entryType;

    @Column(precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "credit_balance_after", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal creditBalanceAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", unique = true, updatable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_return_id", unique = true, updatable = false)
    private PurchaseReturn purchaseReturn;

    @Column(name = "idempotency_key", length = 80, unique = true, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_mode", length = 20, updatable = false)
    private SupplierPaymentMode paymentMode;

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

    protected SupplierLedgerEntry() {
    }

    public static SupplierLedgerEntry purchaseDue(
            Supplier supplier, Purchase purchase, BigDecimal amount,
            BigDecimal balanceAfter, String actorUserId, Instant now) {
        return purchaseDue(
                supplier, purchase, amount, balanceAfter, BigDecimal.ZERO.setScale(2), actorUserId, now);
    }

    public static SupplierLedgerEntry purchaseDue(
            Supplier supplier, Purchase purchase, BigDecimal amount,
            BigDecimal balanceAfter, BigDecimal creditBalanceAfter,
            String actorUserId, Instant now) {
        return create(supplier, SupplierLedgerEntryType.PURCHASE_DUE, amount, balanceAfter,
                creditBalanceAfter, purchase, null, null, null, null,
                "Purchase received on credit", actorUserId, now);
    }

    public static SupplierLedgerEntry purchaseReturn(
            Supplier supplier, PurchaseReturn purchaseReturn, BigDecimal amount,
            BigDecimal balanceAfter, BigDecimal creditBalanceAfter,
            String actorUserId, Instant now) {
        return create(supplier, SupplierLedgerEntryType.PURCHASE_RETURN, amount, balanceAfter,
                creditBalanceAfter, null, purchaseReturn, null, null, null,
                "Goods returned to supplier", actorUserId, now);
    }

    public static SupplierLedgerEntry payment(
            Supplier supplier, BigDecimal amount, BigDecimal balanceAfter,
            SupplierPaymentMode mode, String idempotencyKey, String reference,
            String notes, String actorUserId, Instant now) {
        return payment(
                supplier, amount, balanceAfter, BigDecimal.ZERO.setScale(2), mode,
                idempotencyKey, reference, notes, actorUserId, now);
    }

    public static SupplierLedgerEntry payment(
            Supplier supplier, BigDecimal amount, BigDecimal balanceAfter,
            BigDecimal creditBalanceAfter, SupplierPaymentMode mode,
            String idempotencyKey, String reference,
            String notes, String actorUserId, Instant now) {
        return create(supplier, SupplierLedgerEntryType.PAYMENT, amount, balanceAfter,
                creditBalanceAfter, null, null, idempotencyKey, mode, reference,
                notes, actorUserId, now);
    }

    private static SupplierLedgerEntry create(
            Supplier supplier, SupplierLedgerEntryType type, BigDecimal amount,
            BigDecimal balanceAfter, BigDecimal creditBalanceAfter,
            Purchase purchase, PurchaseReturn purchaseReturn, String idempotencyKey,
            SupplierPaymentMode mode, String reference, String notes,
            String actorUserId, Instant now) {
        SupplierLedgerEntry entry = new SupplierLedgerEntry();
        entry.id = UUID.randomUUID().toString();
        entry.supplier = supplier;
        entry.entryType = type;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.creditBalanceAfter = creditBalanceAfter;
        entry.purchase = purchase;
        entry.purchaseReturn = purchaseReturn;
        entry.idempotencyKey = idempotencyKey;
        entry.paymentMode = mode;
        entry.paymentReference = reference;
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

    public Supplier getSupplier() { return supplier; }
    public SupplierLedgerEntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public BigDecimal getCreditBalanceAfter() { return creditBalanceAfter; }
    public Purchase getPurchase() { return purchase; }
    public PurchaseReturn getPurchaseReturn() { return purchaseReturn; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public SupplierPaymentMode getPaymentMode() { return paymentMode; }
    public String getPaymentReference() { return paymentReference; }
    public String getNotes() { return notes; }
    public String getActorUserId() { return actorUserId; }
    public Instant getOccurredAt() { return occurredAt; }
}
