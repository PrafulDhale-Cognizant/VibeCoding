package com.simplifiedbilling.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "shop_profiles")
public class ShopProfile {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "shop_name", length = 150, nullable = false)
    private String shopName;

    @Column(name = "owner_name", length = 120, nullable = false)
    private String ownerName;

    @Column(name = "address_line1", length = 200, nullable = false)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(length = 100, nullable = false)
    private String city;

    @Column(name = "state_name", length = 100, nullable = false)
    private String stateName;

    @Column(name = "state_code", length = 2, nullable = false)
    private String stateCode;

    @Column(name = "postal_code", length = 10, nullable = false)
    private String postalCode;

    @Column(length = 20, nullable = false)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(name = "gst_registered", nullable = false)
    private boolean gstRegistered;

    @Column(length = 15)
    private String gstin;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(length = 64, nullable = false)
    private String timezone;

    @Column(name = "invoice_prefix", length = 12, nullable = false)
    private String invoicePrefix;

    @Column(name = "financial_year_start_month", nullable = false)
    private byte financialYearStartMonth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "receipt_width", length = 8, nullable = false)
    private ReceiptWidth receiptWidth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "a4_invoice_template", length = 16, nullable = false)
    private A4InvoiceTemplate a4InvoiceTemplate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "thermal_receipt_template", length = 16, nullable = false)
    private ThermalReceiptTemplate thermalReceiptTemplate;

    @Column(name = "logo_file_name")
    private String logoFileName;

    @Column(name = "logo_content_type", length = 50)
    private String logoContentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "logo_data", length = 16_777_215)
    private byte[] logoData;

    @Column(name = "setup_completed_at", nullable = false, updatable = false)
    private Instant setupCompletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopProfile() {
    }

    public static ShopProfile create(ShopProfileData data, Instant now) {
        ShopProfile profile = new ShopProfile();
        profile.id = SINGLETON_ID;
        profile.apply(data);
        profile.setupCompletedAt = now;
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    public void update(ShopProfileData data, Instant now) {
        apply(data);
        updatedAt = now;
    }

    private void apply(ShopProfileData data) {
        shopName = data.shopName();
        ownerName = data.ownerName();
        addressLine1 = data.addressLine1();
        addressLine2 = data.addressLine2();
        city = data.city();
        stateName = data.stateName();
        stateCode = data.stateCode();
        postalCode = data.postalCode();
        phone = data.phone();
        email = data.email();
        gstRegistered = data.gstRegistered();
        gstin = data.gstin();
        currencyCode = data.currencyCode();
        timezone = data.timezone();
        invoicePrefix = data.invoicePrefix();
        financialYearStartMonth = (byte) data.financialYearStartMonth();
        receiptWidth = data.receiptWidth();
        a4InvoiceTemplate = data.a4InvoiceTemplate();
        thermalReceiptTemplate = data.thermalReceiptTemplate();
    }

    public void updateLogo(String fileName, String contentType, byte[] data, Instant now) {
        logoFileName = fileName;
        logoContentType = contentType;
        logoData = data.clone();
        updatedAt = now;
    }

    public void removeLogo(Instant now) {
        logoFileName = null;
        logoContentType = null;
        logoData = null;
        updatedAt = now;
    }

    public byte[] getLogoData() {
        return logoData == null ? null : logoData.clone();
    }

    public String getLogoContentType() {
        return logoContentType;
    }

    public String getLogoFileName() {
        return logoFileName;
    }

    public String getShopName() {
        return shopName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getStateName() {
        return stateName;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public boolean isGstRegistered() {
        return gstRegistered;
    }

    public String getGstin() {
        return gstin;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getInvoicePrefix() {
        return invoicePrefix;
    }

    public int getFinancialYearStartMonth() {
        return financialYearStartMonth;
    }

    public ReceiptWidth getReceiptWidth() {
        return receiptWidth;
    }

    public A4InvoiceTemplate getA4InvoiceTemplate() {
        return a4InvoiceTemplate;
    }

    public ThermalReceiptTemplate getThermalReceiptTemplate() {
        return thermalReceiptTemplate;
    }

    public boolean hasLogo() {
        return logoData != null;
    }

    public long getVersion() {
        return version;
    }

    public Instant getSetupCompletedAt() {
        return setupCompletedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
