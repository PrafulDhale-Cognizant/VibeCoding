package com.simplifiedbilling.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 100, nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public static Category create(String name, Instant now) {
        Category category = new Category();
        category.id = UUID.randomUUID().toString();
        category.name = name;
        category.active = true;
        category.createdAt = now;
        category.updatedAt = now;
        return category;
    }

    public void update(String name, boolean active, Instant now) {
        this.name = name;
        this.active = active;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
