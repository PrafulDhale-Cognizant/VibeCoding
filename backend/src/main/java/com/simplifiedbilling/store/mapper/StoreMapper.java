package com.simplifiedbilling.store.mapper;

import com.simplifiedbilling.store.domain.ShopProfile;
import com.simplifiedbilling.store.domain.ShopProfileData;
import com.simplifiedbilling.store.domain.A4InvoiceTemplate;
import com.simplifiedbilling.store.domain.ThermalReceiptTemplate;
import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.dto.StoreProfileRequest;
import org.springframework.stereotype.Component;

@Component
public class StoreMapper {

    public ShopProfileData toDomain(StoreProfileRequest request) {
        return new ShopProfileData(
                request.shopName().trim(),
                request.ownerName().trim(),
                request.addressLine1().trim(),
                trimToNull(request.addressLine2()),
                request.city().trim(),
                request.stateName().trim(),
                request.stateCode().trim(),
                request.postalCode().trim(),
                request.phone().trim(),
                trimToNull(request.email()),
                request.gstRegistered(),
                request.gstRegistered() ? trimToNull(request.gstin()) : null,
                request.currencyCode(),
                request.timezone(),
                request.invoicePrefix().trim().toUpperCase(),
                request.financialYearStartMonth(),
                request.receiptWidth(),
                request.a4InvoiceTemplate() == null ? A4InvoiceTemplate.MODERN : request.a4InvoiceTemplate(),
                request.thermalReceiptTemplate() == null ? ThermalReceiptTemplate.CLASSIC : request.thermalReceiptTemplate());
    }

    public StoreDetails toDetails(ShopProfile profile) {
        return new StoreDetails(
                profile.getShopName(),
                profile.getOwnerName(),
                profile.getAddressLine1(),
                profile.getAddressLine2(),
                profile.getCity(),
                profile.getStateName(),
                profile.getStateCode(),
                profile.getPostalCode(),
                profile.getPhone(),
                profile.getEmail(),
                profile.isGstRegistered(),
                profile.getGstin(),
                profile.getCurrencyCode(),
                profile.getTimezone(),
                profile.getInvoicePrefix(),
                profile.getFinancialYearStartMonth(),
                profile.getReceiptWidth(),
                profile.getA4InvoiceTemplate(),
                profile.getThermalReceiptTemplate(),
                profile.hasLogo(),
                profile.getVersion(),
                profile.getSetupCompletedAt(),
                profile.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
