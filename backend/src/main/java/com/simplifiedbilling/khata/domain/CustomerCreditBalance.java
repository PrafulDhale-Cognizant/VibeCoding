package com.simplifiedbilling.khata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer_credit_balances")
public class CustomerCreditBalance {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "customer_id", length = 36, nullable = false, updatable = false)
    private String customerId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "outstanding_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal outstandingAmount;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerCreditBalance() {
    }

    static CustomerCreditBalance create(Customer customer, Instant now) {
        CustomerCreditBalance balance = new CustomerCreditBalance();
        balance.customer = customer;
        balance.customerId = customer.getId();
        balance.outstandingAmount = BigDecimal.ZERO.setScale(2);
        balance.updatedAt = now;
        return balance;
    }

    public BigDecimal addCredit(BigDecimal amount, Instant now) {
        outstandingAmount = outstandingAmount.add(amount);
        updatedAt = now;
        return outstandingAmount;
    }

    public BigDecimal settle(BigDecimal amount, Instant now) {
        if (amount.compareTo(outstandingAmount) > 0) {
            throw new IllegalArgumentException("Settlement cannot exceed outstanding balance.");
        }
        outstandingAmount = outstandingAmount.subtract(amount);
        updatedAt = now;
        return outstandingAmount;
    }

    public String getCustomerId() { return customerId; }
    public Customer getCustomer() { return customer; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public long getVersion() { return version == null ? 0L : version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
