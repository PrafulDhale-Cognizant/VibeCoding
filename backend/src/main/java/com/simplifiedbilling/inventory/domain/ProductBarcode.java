package com.simplifiedbilling.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_barcodes")
public class ProductBarcode {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "barcode_value", length = 64, nullable = false, unique = true)
    private String value;

    @Column(name = "internal_barcode", nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductBarcode() {
    }

    static ProductBarcode create(Product product, String value, boolean internal, Instant now) {
        ProductBarcode barcode = new ProductBarcode();
        barcode.id = UUID.randomUUID().toString();
        barcode.product = product;
        barcode.value = value;
        barcode.internal = internal;
        barcode.createdAt = now;
        return barcode;
    }

    void update(String value, boolean internal) {
        this.value = value;
        this.internal = internal;
    }

    public String getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getValue() {
        return value;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
