package com.simplifiedbilling.purchasing.domain;

import com.simplifiedbilling.purchasing.service.PurchasePricingResult;
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
@Table(name = "purchases")
public class Purchase implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "purchase_number", length = 40, nullable = false, unique = true, updatable = false)
    private String purchaseNumber;

    @Column(name = "idempotency_key", length = 80, nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;

    @Column(name = "supplier_name", length = 150, nullable = false, updatable = false)
    private String supplierName;

    @Column(name = "supplier_invoice_number", length = 80, updatable = false)
    private String supplierInvoiceNumber;

    @Column(name = "invoice_date", nullable = false, updatable = false)
    private LocalDate invoiceDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16, nullable = false, updatable = false)
    private PurchaseStatus status;

    @Column(name = "prices_include_tax", nullable = false, updatable = false)
    private boolean pricesIncludeTax;

    @Column(name = "subtotal_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal subtotalAmount;

    @Column(name = "tax_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal amountPaid;

    @Column(name = "outstanding_added", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal outstandingAdded;

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

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    @Transient
    private boolean newEntity = true;

    protected Purchase() {
    }

    public static Purchase received(
            String id,
            String purchaseNumber,
            String idempotencyKey,
            Supplier supplier,
            String supplierInvoiceNumber,
            LocalDate invoiceDate,
            PurchasePricingResult pricing,
            BigDecimal amountPaid,
            SupplierPaymentMode paymentMode,
            String paymentReference,
            String notes,
            String actorUserId,
            Instant now) {
        Purchase purchase = new Purchase();
        purchase.id = id;
        purchase.purchaseNumber = purchaseNumber;
        purchase.idempotencyKey = idempotencyKey;
        purchase.supplier = supplier;
        purchase.supplierName = supplier.getName();
        purchase.supplierInvoiceNumber = supplierInvoiceNumber;
        purchase.invoiceDate = invoiceDate;
        purchase.status = PurchaseStatus.RECEIVED;
        purchase.pricesIncludeTax = pricing.pricesIncludeTax();
        purchase.subtotalAmount = pricing.subtotalAmount();
        purchase.taxAmount = pricing.taxAmount();
        purchase.totalAmount = pricing.totalAmount();
        purchase.amountPaid = amountPaid;
        purchase.outstandingAdded = pricing.totalAmount().subtract(amountPaid);
        purchase.paymentMode = paymentMode;
        purchase.paymentReference = paymentReference;
        purchase.notes = notes;
        purchase.actorUserId = actorUserId;
        purchase.receivedAt = now;
        purchase.createdAt = now;
        pricing.lines().forEach(line -> purchase.items.add(PurchaseItem.create(purchase, line)));
        return purchase;
    }

    @Override
    public String getId() { return id; }
    @Override
    public boolean isNew() { return newEntity; }
    @PostLoad
    @PostPersist
    void markNotNew() { newEntity = false; }

    public String getPurchaseNumber() { return purchaseNumber; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Supplier getSupplier() { return supplier; }
    public String getSupplierName() { return supplierName; }
    public String getSupplierInvoiceNumber() { return supplierInvoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public PurchaseStatus getStatus() { return status; }
    public boolean isPricesIncludeTax() { return pricesIncludeTax; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getOutstandingAdded() { return outstandingAdded; }
    public SupplierPaymentMode getPaymentMode() { return paymentMode; }
    public String getPaymentReference() { return paymentReference; }
    public String getNotes() { return notes; }
    public String getActorUserId() { return actorUserId; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PurchaseItem> getItems() { return Collections.unmodifiableList(items); }
}
