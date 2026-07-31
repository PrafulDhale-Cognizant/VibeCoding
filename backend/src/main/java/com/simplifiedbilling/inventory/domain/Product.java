package com.simplifiedbilling.inventory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 150, nullable = false)
    private String name;

    @Column(name = "receipt_name", length = 80, nullable = false)
    private String receiptName;

    @Column(length = 64, unique = true)
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "unit_code", length = 20, nullable = false)
    private ProductUnit unit;

    @Column(name = "hsn_code", length = 16)
    private String hsnCode;

    @Column(name = "gst_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal gstRate;

    @Column(name = "purchase_cost", precision = 19, scale = 2, nullable = false)
    private BigDecimal purchaseCost;

    @Column(name = "selling_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal sellingPrice;

    @Column(name = "minimum_stock_level", precision = 19, scale = 3, nullable = false)
    private BigDecimal minimumStockLevel;

    @Column(nullable = false)
    private boolean active;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private ProductBarcode barcode;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private InventoryBalance stockBalance;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newEntity = true;

    protected Product() {
    }

    public static Product create(
            ProductData data,
            Category category,
            String barcodeValue,
            boolean internalBarcode,
            BigDecimal openingStock,
            Instant now) {

        Product product = new Product();
        product.id = UUID.randomUUID().toString();
        product.apply(data, category, now);
        product.createdAt = now;
        product.barcode = ProductBarcode.create(product, barcodeValue, internalBarcode, now);
        product.stockBalance = InventoryBalance.create(product, openingStock, now);
        return product;
    }

    public void update(
            ProductData data,
            Category category,
            String barcodeValue,
            boolean internalBarcode,
            Instant now) {

        apply(data, category, now);
        barcode.update(barcodeValue, internalBarcode);
    }

    public void updatePurchaseCost(BigDecimal cost, Instant now) {
        if (cost == null || cost.signum() < 0) {
            throw new IllegalArgumentException("Purchase cost cannot be negative.");
        }
        purchaseCost = cost;
        updatedAt = now;
    }

    private void apply(ProductData data, Category category, Instant now) {
        name = data.name();
        receiptName = data.receiptName();
        sku = data.sku();
        this.category = category;
        unit = data.unit();
        hsnCode = data.hsnCode();
        gstRate = data.gstRate();
        purchaseCost = data.purchaseCost();
        sellingPrice = data.sellingPrice();
        minimumStockLevel = data.minimumStockLevel();
        active = data.active();
        updatedAt = now;
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

    public String getName() {
        return name;
    }

    public String getReceiptName() {
        return receiptName;
    }

    public String getSku() {
        return sku;
    }

    public Category getCategory() {
        return category;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public String getHsnCode() {
        return hsnCode;
    }

    public BigDecimal getGstRate() {
        return gstRate;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public BigDecimal getMinimumStockLevel() {
        return minimumStockLevel;
    }

    public boolean isActive() {
        return active;
    }

    public ProductBarcode getBarcode() {
        return barcode;
    }

    public InventoryBalance getStockBalance() {
        return stockBalance;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
