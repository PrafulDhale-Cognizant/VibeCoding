package com.simplifiedbilling.pos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_mode", length = 16, nullable = false)
    private PaymentMode mode;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "tendered_amount", precision = 19, scale = 2)
    private BigDecimal tenderedAmount;

    @Column(name = "change_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal changeAmount;

    @Column(name = "payment_reference", length = 100)
    private String reference;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected Payment() {
    }

    static Payment create(Invoice invoice, PaymentAllocation allocation, Instant now) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID().toString();
        payment.invoice = invoice;
        payment.mode = allocation.mode();
        payment.amount = allocation.amount();
        payment.tenderedAmount = allocation.tenderedAmount();
        payment.changeAmount = allocation.changeAmount();
        payment.reference = allocation.reference();
        payment.recordedAt = now;
        return payment;
    }

    public PaymentMode getMode() { return mode; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getTenderedAmount() { return tenderedAmount; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public String getReference() { return reference; }
    public Instant getRecordedAt() { return recordedAt; }
}
