package com.simplifiedbilling.pos.domain;

import com.simplifiedbilling.pos.service.RefundAllocation;
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
@Table(name = "refund_records")
public class RefundRecord {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_return_id", nullable = false, updatable = false)
    private SaleReturn saleReturn;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "refund_mode", length = 16, nullable = false, updatable = false) private PaymentMode mode;
    @Column(precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal amount;
    @Column(name = "refund_reference", length = 100, updatable = false) private String reference;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "customer_id", length = 36, updatable = false) private String customerId;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;

    protected RefundRecord() { }
    static RefundRecord create(SaleReturn saleReturn, RefundAllocation allocation, Instant now) {
        RefundRecord refund = new RefundRecord(); refund.id = UUID.randomUUID().toString();
        refund.saleReturn = saleReturn; refund.mode = allocation.mode(); refund.amount = allocation.amount();
        refund.reference = allocation.reference(); refund.customerId = allocation.customerId(); refund.recordedAt = now;
        return refund;
    }
    public PaymentMode getMode() { return mode; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }
    public String getCustomerId() { return customerId; }
    public Instant getRecordedAt() { return recordedAt; }
}
