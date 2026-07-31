package com.simplifiedbilling.purchasing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 150, nullable = false)
    private String name;

    @Column(length = 15, nullable = false, unique = true)
    private String phone;

    @Column(length = 15, unique = true)
    private String gstin;

    @Column(length = 500)
    private String address;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private SupplierPayableBalance payableBalance;

    @Transient
    private boolean newEntity = true;

    protected Supplier() {
    }

    public static Supplier create(
            String name, String phone, String gstin, String address, String notes, Instant now) {
        Supplier supplier = new Supplier();
        supplier.id = UUID.randomUUID().toString();
        supplier.name = name;
        supplier.phone = phone;
        supplier.gstin = gstin;
        supplier.address = address;
        supplier.notes = notes;
        supplier.active = true;
        supplier.createdAt = now;
        supplier.updatedAt = now;
        supplier.payableBalance = SupplierPayableBalance.create(supplier, now);
        return supplier;
    }

    public void update(
            String name, String phone, String gstin, String address,
            String notes, boolean active, Instant now) {
        this.name = name;
        this.phone = phone;
        this.gstin = gstin;
        this.address = address;
        this.notes = notes;
        this.active = active;
        this.updatedAt = now;
    }

    @Override
    public String getId() { return id; }
    @Override
    public boolean isNew() { return newEntity; }
    @PostLoad
    @PostPersist
    void markNotNew() { newEntity = false; }

    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getGstin() { return gstin; }
    public String getAddress() { return address; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public long getVersion() { return version == null ? 0L : version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public SupplierPayableBalance getPayableBalance() { return payableBalance; }
}
