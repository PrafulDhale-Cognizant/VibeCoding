package com.simplifiedbilling.purchasing.domain;

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
@Table(name = "supplier_payable_balances")
public class SupplierPayableBalance {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "supplier_id", length = 36, nullable = false, updatable = false)
    private String supplierId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "outstanding_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal outstandingAmount;

    @Column(name = "credit_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal creditAmount;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupplierPayableBalance() {
    }

    static SupplierPayableBalance create(Supplier supplier, Instant now) {
        SupplierPayableBalance balance = new SupplierPayableBalance();
        balance.supplier = supplier;
        balance.supplierId = supplier.getId();
        balance.outstandingAmount = BigDecimal.ZERO.setScale(2);
        balance.creditAmount = BigDecimal.ZERO.setScale(2);
        balance.updatedAt = now;
        return balance;
    }

    public BigDecimal addPayable(BigDecimal amount, Instant now) {
        outstandingAmount = outstandingAmount.add(amount);
        updatedAt = now;
        return outstandingAmount;
    }

    public SupplierBalanceMovement applyPurchase(BigDecimal amount, Instant now) {
        BigDecimal creditUsed = creditAmount.min(amount);
        creditAmount = creditAmount.subtract(creditUsed);
        BigDecimal payableAdded = amount.subtract(creditUsed);
        outstandingAmount = outstandingAmount.add(payableAdded);
        updatedAt = now;
        return new SupplierBalanceMovement(
                payableAdded, BigDecimal.ZERO.setScale(2), outstandingAmount, creditAmount);
    }

    public SupplierBalanceMovement applyReturn(BigDecimal amount, Instant now) {
        BigDecimal payableReduction = outstandingAmount.min(amount);
        outstandingAmount = outstandingAmount.subtract(payableReduction);
        BigDecimal creditAdded = amount.subtract(payableReduction);
        creditAmount = creditAmount.add(creditAdded);
        updatedAt = now;
        return new SupplierBalanceMovement(
                payableReduction, creditAdded, outstandingAmount, creditAmount);
    }

    public BigDecimal pay(BigDecimal amount, Instant now) {
        if (amount.compareTo(outstandingAmount) > 0) {
            throw new IllegalArgumentException("Payment cannot exceed supplier outstanding balance.");
        }
        outstandingAmount = outstandingAmount.subtract(amount);
        updatedAt = now;
        return outstandingAmount;
    }

    public String getSupplierId() { return supplierId; }
    public Supplier getSupplier() { return supplier; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public long getVersion() { return version == null ? 0L : version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
