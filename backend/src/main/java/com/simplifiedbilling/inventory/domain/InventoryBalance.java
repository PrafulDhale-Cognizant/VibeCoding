package com.simplifiedbilling.inventory.domain;

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
@Table(name = "inventory_balances")
public class InventoryBalance {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_id", length = 36, nullable = false, updatable = false)
    private String productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(precision = 19, scale = 3, nullable = false)
    private BigDecimal quantity;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryBalance() {
    }

    static InventoryBalance create(Product product, BigDecimal openingStock, Instant now) {
        InventoryBalance balance = new InventoryBalance();
        balance.product = product;
        balance.productId = product.getId();
        balance.quantity = openingStock;
        balance.updatedAt = now;
        return balance;
    }

    public BigDecimal adjust(BigDecimal delta, Instant now) {
        BigDecimal newQuantity = quantity.add(delta);
        if (newQuantity.signum() < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative.");
        }
        quantity = newQuantity;
        updatedAt = now;
        return quantity;
    }

    public String getProductId() {
        return productId;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
