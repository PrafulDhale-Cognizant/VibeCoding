package com.simplifiedbilling.pos.domain;

import com.simplifiedbilling.inventory.domain.ProductUnit;
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
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_id", length = 36, nullable = false, updatable = false)
    private String productId;

    @Column(name = "product_name", length = 150, nullable = false)
    private String productName;

    @Column(name = "receipt_name", length = 80, nullable = false)
    private String receiptName;

    @Column(length = 64, nullable = false)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "unit_code", length = 20, nullable = false)
    private ProductUnit unit;

    @Column(precision = 19, scale = 3, nullable = false)
    private BigDecimal quantity;
    @Column(name = "purchase_cost", precision = 19, scale = 2, nullable = false)
    private BigDecimal purchaseCost;
    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal unitPrice;
    @Column(name = "gst_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal gstRate;
    @Column(name = "gross_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal grossAmount;
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
    @Column(name = "line_total", precision = 19, scale = 2, nullable = false)
    private BigDecimal lineTotal;

    protected InvoiceItem() {
    }

    static InvoiceItem create(Invoice invoice, PricingLine line) {
        InvoiceItem item = new InvoiceItem();
        item.id = UUID.randomUUID().toString();
        item.invoice = invoice;
        item.lineNumber = line.lineNumber();
        item.productId = line.product().productId();
        item.productName = line.product().name();
        item.receiptName = line.product().receiptName();
        item.barcode = line.product().barcode();
        item.unit = line.product().unit();
        item.quantity = line.quantity();
        item.purchaseCost = line.product().purchaseCost();
        item.unitPrice = line.product().sellingPrice();
        item.gstRate = line.product().gstRate();
        item.grossAmount = line.grossAmount();
        item.lineDiscountAmount = line.lineDiscountAmount();
        item.billDiscountAmount = line.billDiscountAmount();
        item.taxableAmount = line.taxableAmount();
        item.cgstAmount = line.cgstAmount();
        item.sgstAmount = line.sgstAmount();
        item.igstAmount = line.igstAmount();
        item.lineTotal = line.lineTotal();
        return item;
    }

    public int getLineNumber() { return lineNumber; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getReceiptName() { return receiptName; }
    public String getBarcode() { return barcode; }
    public ProductUnit getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPurchaseCost() { return purchaseCost; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getGstRate() { return gstRate; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getLineDiscountAmount() { return lineDiscountAmount; }
    public BigDecimal getBillDiscountAmount() { return billDiscountAmount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getCgstAmount() { return cgstAmount; }
    public BigDecimal getSgstAmount() { return sgstAmount; }
    public BigDecimal getIgstAmount() { return igstAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
