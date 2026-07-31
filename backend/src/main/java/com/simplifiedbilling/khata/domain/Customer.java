package com.simplifiedbilling.khata.domain;

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
@Table(name = "customers")
public class Customer implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 150, nullable = false)
    private String name;

    @Column(length = 15, nullable = false, unique = true)
    private String phone;

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

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private CustomerCreditBalance creditBalance;

    @Transient
    private boolean newEntity = true;

    protected Customer() {
    }

    public static Customer create(String name, String phone, String notes, Instant now) {
        Customer customer = new Customer();
        customer.id = UUID.randomUUID().toString();
        customer.name = name;
        customer.phone = phone;
        customer.notes = notes;
        customer.active = true;
        customer.createdAt = now;
        customer.updatedAt = now;
        customer.creditBalance = CustomerCreditBalance.create(customer, now);
        return customer;
    }

    public void update(String name, String phone, String notes, boolean active, Instant now) {
        this.name = name;
        this.phone = phone;
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
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public long getVersion() { return version == null ? 0L : version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public CustomerCreditBalance getCreditBalance() { return creditBalance; }
}
