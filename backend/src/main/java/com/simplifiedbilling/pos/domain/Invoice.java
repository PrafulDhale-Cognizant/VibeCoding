package com.simplifiedbilling.pos.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "invoice_number", length = 40, nullable = false, unique = true, updatable = false)
    private String invoiceNumber;

    @Column(name = "idempotency_key", length = 80, nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16, nullable = false)
    private InvoiceStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "cashier_user_id", length = 36, nullable = false, updatable = false)
    private String cashierUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tax_mode", length = 20, nullable = false)
    private TaxMode taxMode;

    @Column(name = "prices_include_gst", nullable = false)
    private boolean pricesIncludeGst;

    @Column(name = "subtotal_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal subtotalAmount;

    @Column(name = "line_discount_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal lineDiscountAmount;

    @Column(name = "bill_discount_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal billDiscountAmount;

    @Column(name = "taxable_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal taxableAmount;

    @Column(name = "cgst_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal sgstAmount;

    @Column(name = "igst_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal igstAmount;

    @Column(name = "round_off_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal roundOffAmount;

    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(length = 500)
    private String notes;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    @Transient
    private boolean newEntity = true;

    protected Invoice() {
    }

    public static Invoice completed(
            String id,
            String invoiceNumber,
            String idempotencyKey,
            String cashierUserId,
            PricingResult pricing,
            List<PaymentAllocation> allocations,
            String notes,
            Instant now) {
        Invoice invoice = new Invoice();
        invoice.id = id;
        invoice.invoiceNumber = invoiceNumber;
        invoice.idempotencyKey = idempotencyKey;
        invoice.status = InvoiceStatus.COMPLETED;
        invoice.cashierUserId = cashierUserId;
        invoice.taxMode = pricing.taxMode();
        invoice.pricesIncludeGst = pricing.pricesIncludeGst();
        invoice.subtotalAmount = pricing.subtotalAmount();
        invoice.lineDiscountAmount = pricing.lineDiscountAmount();
        invoice.billDiscountAmount = pricing.billDiscountAmount();
        invoice.taxableAmount = pricing.taxableAmount();
        invoice.cgstAmount = pricing.cgstAmount();
        invoice.sgstAmount = pricing.sgstAmount();
        invoice.igstAmount = pricing.igstAmount();
        invoice.roundOffAmount = pricing.roundOffAmount();
        invoice.totalAmount = pricing.totalAmount();
        invoice.notes = notes;
        invoice.completedAt = now;
        invoice.createdAt = now;
        pricing.lines().forEach(line -> invoice.items.add(InvoiceItem.create(invoice, line)));
        allocations.forEach(allocation -> invoice.payments.add(Payment.create(invoice, allocation, now)));
        return invoice;
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

    public String getInvoiceNumber() { return invoiceNumber; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public InvoiceStatus getStatus() { return status; }
    public String getCashierUserId() { return cashierUserId; }
    public TaxMode getTaxMode() { return taxMode; }
    public boolean isPricesIncludeGst() { return pricesIncludeGst; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getLineDiscountAmount() { return lineDiscountAmount; }
    public BigDecimal getBillDiscountAmount() { return billDiscountAmount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getCgstAmount() { return cgstAmount; }
    public BigDecimal getSgstAmount() { return sgstAmount; }
    public BigDecimal getIgstAmount() { return igstAmount; }
    public BigDecimal getRoundOffAmount() { return roundOffAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getNotes() { return notes; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<InvoiceItem> getItems() { return Collections.unmodifiableList(items); }
    public List<Payment> getPayments() { return Collections.unmodifiableList(payments); }

    public void recordReturn(boolean allItemsReturned, boolean cancellation) {
        if (status == InvoiceStatus.CANCELLED || status == InvoiceStatus.RETURNED) {
            throw new IllegalStateException("Invoice has already been fully reversed.");
        }
        status = cancellation ? InvoiceStatus.CANCELLED
                : allItemsReturned ? InvoiceStatus.RETURNED : InvoiceStatus.PARTIALLY_RETURNED;
    }
}
