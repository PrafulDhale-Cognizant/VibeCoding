package com.simplifiedbilling.purchasing.domain;

import com.simplifiedbilling.purchasing.service.PurchasePricingResult;
import com.simplifiedbilling.purchasing.service.PurchaseReturnSelection;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "purchase_returns")
public class PurchaseReturn implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "return_number", length = 40, nullable = false, unique = true, updatable = false)
    private String returnNumber;

    @Column(name = "idempotency_key", length = 80, nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;

    @Column(name = "supplier_name", length = 150, nullable = false, updatable = false)
    private String supplierName;

    @Column(name = "return_date", nullable = false, updatable = false)
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 24, nullable = false, updatable = false)
    private PurchaseReturnReason reason;

    @Column(name = "subtotal_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal subtotalAmount;

    @Column(name = "tax_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "payable_reduction", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal payableReduction;

    @Column(name = "credit_added", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal creditAdded;

    @Column(length = 500, updatable = false)
    private String notes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_user_id", length = 36, nullable = false, updatable = false)
    private String actorUserId;

    @Column(name = "returned_at", nullable = false, updatable = false)
    private Instant returnedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReturnItem> items = new ArrayList<>();

    @Transient
    private boolean newEntity = true;

    protected PurchaseReturn() {
    }

    public static PurchaseReturn completed(
            String id,
            String returnNumber,
            String idempotencyKey,
            Purchase purchase,
            LocalDate returnDate,
            PurchaseReturnReason reason,
            PurchasePricingResult pricing,
            List<PurchaseReturnSelection> selections,
            SupplierBalanceMovement movement,
            String notes,
            String actorUserId,
            Instant now) {
        PurchaseReturn purchaseReturn = new PurchaseReturn();
        purchaseReturn.id = id;
        purchaseReturn.returnNumber = returnNumber;
        purchaseReturn.idempotencyKey = idempotencyKey;
        purchaseReturn.purchase = purchase;
        purchaseReturn.supplier = purchase.getSupplier();
        purchaseReturn.supplierName = purchase.getSupplierName();
        purchaseReturn.returnDate = returnDate;
        purchaseReturn.reason = reason;
        purchaseReturn.subtotalAmount = pricing.subtotalAmount();
        purchaseReturn.taxAmount = pricing.taxAmount();
        purchaseReturn.totalAmount = pricing.totalAmount();
        purchaseReturn.payableReduction = movement.payableMovement();
        purchaseReturn.creditAdded = movement.creditAdded();
        purchaseReturn.notes = notes;
        purchaseReturn.actorUserId = actorUserId;
        purchaseReturn.returnedAt = now;
        purchaseReturn.createdAt = now;
        for (int index = 0; index < selections.size(); index++) {
            purchaseReturn.items.add(PurchaseReturnItem.create(
                    purchaseReturn, selections.get(index), pricing.lines().get(index)));
        }
        return purchaseReturn;
    }

    @Override
    public String getId() { return id; }
    @Override
    public boolean isNew() { return newEntity; }
    @PostLoad
    @PostPersist
    void markNotNew() { newEntity = false; }

    public String getReturnNumber() { return returnNumber; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Purchase getPurchase() { return purchase; }
    public Supplier getSupplier() { return supplier; }
    public String getSupplierName() { return supplierName; }
    public LocalDate getReturnDate() { return returnDate; }
    public PurchaseReturnReason getReason() { return reason; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPayableReduction() { return payableReduction; }
    public BigDecimal getCreditAdded() { return creditAdded; }
    public String getNotes() { return notes; }
    public String getActorUserId() { return actorUserId; }
    public Instant getReturnedAt() { return returnedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PurchaseReturnItem> getItems() { return Collections.unmodifiableList(items); }
}
