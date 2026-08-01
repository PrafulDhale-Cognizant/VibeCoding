package com.simplifiedbilling.pos.domain;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.pos.service.SaleReturnLineSelection;
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
@Table(name = "sale_return_items")
public class SaleReturnItem {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_return_id", nullable = false, updatable = false)
    private SaleReturn saleReturn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id", nullable = false, updatable = false)
    private InvoiceItem invoiceItem;
    @Column(name = "line_number", nullable = false, updatable = false) private int lineNumber;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "product_id", length = 36, nullable = false, updatable = false)
    private String productId;
    @Column(name = "product_name", length = 150, nullable = false, updatable = false) private String productName;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "unit_code", length = 20, nullable = false, updatable = false) private ProductUnit unit;
    @Column(precision = 19, scale = 3, nullable = false, updatable = false) private BigDecimal quantity;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16, nullable = false, updatable = false) private ReturnDisposition disposition;
    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal unitPrice;
    @Column(name = "gst_rate", precision = 5, scale = 2, nullable = false, updatable = false) private BigDecimal gstRate;
    @Column(name = "gross_amount", precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal grossAmount;
    @Column(name = "discount_amount", precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal discountAmount;
    @Column(name = "taxable_amount", precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal taxableAmount;
    @Column(name = "cgst_amount", precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal cgstAmount;
    @Column(name = "sgst_amount", precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal sgstAmount;
    @Column(name = "igst_amount", precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal igstAmount;
    @Column(name = "line_total", precision = 19, scale = 2, nullable = false, updatable = false) private BigDecimal lineTotal;

    protected SaleReturnItem() { }

    static SaleReturnItem create(SaleReturn saleReturn, SaleReturnLineSelection selection) {
        InvoiceItem source = selection.invoiceItem();
        SaleReturnItem item = new SaleReturnItem();
        item.id = UUID.randomUUID().toString(); item.saleReturn = saleReturn; item.invoiceItem = source;
        item.lineNumber = source.getLineNumber(); item.productId = source.getProductId();
        item.productName = source.getProductName(); item.unit = source.getUnit();
        item.quantity = selection.quantity(); item.disposition = selection.disposition();
        item.unitPrice = source.getUnitPrice(); item.gstRate = source.getGstRate();
        item.grossAmount = selection.grossAmount(); item.discountAmount = selection.discountAmount();
        item.taxableAmount = selection.taxableAmount(); item.cgstAmount = selection.cgstAmount();
        item.sgstAmount = selection.sgstAmount(); item.igstAmount = selection.igstAmount();
        item.lineTotal = selection.lineTotal();
        return item;
    }

    public String getId() { return id; }
    public InvoiceItem getInvoiceItem() { return invoiceItem; }
    public int getLineNumber() { return lineNumber; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public ProductUnit getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public ReturnDisposition getDisposition() { return disposition; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getGstRate() { return gstRate; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getCgstAmount() { return cgstAmount; }
    public BigDecimal getSgstAmount() { return sgstAmount; }
    public BigDecimal getIgstAmount() { return igstAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
