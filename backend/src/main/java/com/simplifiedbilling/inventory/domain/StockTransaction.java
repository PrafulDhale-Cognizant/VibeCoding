package com.simplifiedbilling.inventory.domain;

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
@Table(name = "stock_transactions")
public class StockTransaction implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "transaction_type", length = 32, nullable = false)
    private StockTransactionType transactionType;

    @Column(name = "quantity_delta", precision = 19, scale = 3, nullable = false)
    private BigDecimal quantityDelta;

    @Column(name = "balance_after", precision = 19, scale = 3, nullable = false)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason_code", length = 32, nullable = false)
    private StockReasonCode reasonCode;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(length = 500)
    private String notes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_user_id", length = 36)
    private String actorUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Transient
    private boolean newEntity = true;

    protected StockTransaction() {
    }

    public static StockTransaction create(
            Product product,
            StockTransactionType transactionType,
            BigDecimal quantityDelta,
            BigDecimal balanceAfter,
            StockReasonCode reasonCode,
            String referenceType,
            String referenceId,
            String notes,
            String actorUserId,
            Instant occurredAt) {

        StockTransaction transaction = new StockTransaction();
        transaction.id = UUID.randomUUID().toString();
        transaction.product = product;
        transaction.transactionType = transactionType;
        transaction.quantityDelta = quantityDelta;
        transaction.balanceAfter = balanceAfter;
        transaction.reasonCode = reasonCode;
        transaction.referenceType = referenceType;
        transaction.referenceId = referenceId;
        transaction.notes = notes;
        transaction.actorUserId = actorUserId;
        transaction.occurredAt = occurredAt;
        return transaction;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    public Product getProduct() {
        return product;
    }

    public StockTransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public StockReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getNotes() {
        return notes;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
