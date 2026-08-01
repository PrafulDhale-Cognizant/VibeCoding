package com.simplifiedbilling.pos.domain;

import com.simplifiedbilling.pos.service.RefundAllocation;
import com.simplifiedbilling.pos.service.SaleReturnLineSelection;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "sale_returns")
public class SaleReturn implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "return_number", length = 40, nullable = false, unique = true, updatable = false)
    private String returnNumber;
    @Column(name = "idempotency_key", length = 80, nullable = false, unique = true, updatable = false)
    private String idempotencyKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "return_type", length = 16, nullable = false, updatable = false)
    private SaleReturnType type;
    @Column(length = 500, nullable = false, updatable = false)
    private String reason;
    @Column(name = "subtotal_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal subtotalAmount;
    @Column(name = "discount_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal discountAmount;
    @Column(name = "taxable_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal taxableAmount;
    @Column(name = "cgst_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal cgstAmount;
    @Column(name = "sgst_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal sgstAmount;
    @Column(name = "igst_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal igstAmount;
    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal totalAmount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_user_id", length = 36, nullable = false, updatable = false)
    private String actorUserId;
    @Column(name = "returned_at", nullable = false, updatable = false)
    private Instant returnedAt;
    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleReturnItem> items = new ArrayList<>();
    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefundRecord> refunds = new ArrayList<>();
    @Transient
    private boolean newEntity = true;

    protected SaleReturn() { }

    public static SaleReturn completed(
            String id, String returnNumber, String idempotencyKey, Invoice invoice,
            SaleReturnType type, String reason, List<SaleReturnLineSelection> selections,
            List<RefundAllocation> allocations, String actorUserId, Instant now) {
        SaleReturn result = new SaleReturn();
        result.id = id;
        result.returnNumber = returnNumber;
        result.idempotencyKey = idempotencyKey;
        result.invoice = invoice;
        result.type = type;
        result.reason = reason;
        result.subtotalAmount = sum(selections, SaleReturnLineSelection::grossAmount);
        result.discountAmount = sum(selections, SaleReturnLineSelection::discountAmount);
        result.taxableAmount = sum(selections, SaleReturnLineSelection::taxableAmount);
        result.cgstAmount = sum(selections, SaleReturnLineSelection::cgstAmount);
        result.sgstAmount = sum(selections, SaleReturnLineSelection::sgstAmount);
        result.igstAmount = sum(selections, SaleReturnLineSelection::igstAmount);
        result.totalAmount = sum(selections, SaleReturnLineSelection::lineTotal);
        result.actorUserId = actorUserId;
        result.returnedAt = now;
        selections.forEach(selection -> result.items.add(SaleReturnItem.create(result, selection)));
        allocations.forEach(allocation -> result.refunds.add(RefundRecord.create(result, allocation, now)));
        return result;
    }

    private static BigDecimal sum(
            List<SaleReturnLineSelection> lines,
            java.util.function.Function<SaleReturnLineSelection, BigDecimal> extractor) {
        return lines.stream().map(extractor).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    @Override public String getId() { return id; }
    @Override public boolean isNew() { return newEntity; }
    @PostLoad @PostPersist void markNotNew() { newEntity = false; }
    public String getReturnNumber() { return returnNumber; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Invoice getInvoice() { return invoice; }
    public SaleReturnType getType() { return type; }
    public String getReason() { return reason; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getCgstAmount() { return cgstAmount; }
    public BigDecimal getSgstAmount() { return sgstAmount; }
    public BigDecimal getIgstAmount() { return igstAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getActorUserId() { return actorUserId; }
    public Instant getReturnedAt() { return returnedAt; }
    public List<SaleReturnItem> getItems() { return Collections.unmodifiableList(items); }
    public List<RefundRecord> getRefunds() { return Collections.unmodifiableList(refunds); }
}
