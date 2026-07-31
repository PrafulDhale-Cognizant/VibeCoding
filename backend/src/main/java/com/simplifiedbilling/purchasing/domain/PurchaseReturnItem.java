package com.simplifiedbilling.purchasing.domain;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.purchasing.service.PurchasePricingLine;
import com.simplifiedbilling.purchasing.service.PurchaseReturnSelection;
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
import java.util.UUID;

@Entity
@Table(name = "purchase_return_items")
public class PurchaseReturnItem {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_return_id", nullable = false, updatable = false)
    private PurchaseReturn purchaseReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_item_id", nullable = false, updatable = false)
    private PurchaseItem purchaseItem;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_id", length = 36, nullable = false, updatable = false)
    private String productId;

    @Column(name = "product_name", length = 150, nullable = false, updatable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "unit_code", length = 20, nullable = false, updatable = false)
    private ProductUnit unit;

    @Column(precision = 19, scale = 3, nullable = false, updatable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "gst_rate", precision = 5, scale = 2, nullable = false, updatable = false)
    private BigDecimal gstRate;

    @Column(name = "taxable_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal taxAmount;

    @Column(name = "line_total", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal lineTotal;

    protected PurchaseReturnItem() {
    }

    static PurchaseReturnItem create(
            PurchaseReturn purchaseReturn,
            PurchaseReturnSelection selection,
            PurchasePricingLine pricing) {
        PurchaseItem source = selection.purchaseItem();
        PurchaseReturnItem item = new PurchaseReturnItem();
        item.id = UUID.randomUUID().toString();
        item.purchaseReturn = purchaseReturn;
        item.purchaseItem = source;
        item.lineNumber = pricing.lineNumber();
        item.productId = source.getProductId();
        item.productName = source.getProductName();
        item.unit = source.getUnit();
        item.quantity = selection.quantity();
        item.unitCost = source.getUnitCost();
        item.gstRate = source.getGstRate();
        item.taxableAmount = pricing.taxableAmount();
        item.taxAmount = pricing.taxAmount();
        item.lineTotal = pricing.lineTotal();
        return item;
    }

    public String getId() { return id; }
    public PurchaseItem getPurchaseItem() { return purchaseItem; }
    public int getLineNumber() { return lineNumber; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public ProductUnit getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getGstRate() { return gstRate; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
