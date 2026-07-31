package com.simplifiedbilling.inventory.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 80) String receiptName,
        @Size(max = 64) @Pattern(regexp = "^$|[A-Za-z0-9._-]+$") String sku,
        @Size(max = 64) @Pattern(regexp = "^$|[A-Za-z0-9._-]+$") String barcode,
        boolean generateBarcode,
        @NotBlank String categoryId,
        @NotNull ProductUnit unit,
        @Size(max = 16) @Pattern(regexp = "^$|[0-9]+$") String hsnCode,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
        BigDecimal gstRate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
        BigDecimal purchaseCost,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
        BigDecimal sellingPrice,
        @NotNull @DecimalMin("0.000") @Digits(integer = 16, fraction = 3)
        BigDecimal openingStock,
        @NotNull @DecimalMin("0.000") @Digits(integer = 16, fraction = 3)
        BigDecimal minimumStockLevel) {

    @AssertTrue(message = "Provide a barcode or request an internally generated barcode, but not both.")
    public boolean isBarcodeSelectionValid() {
        boolean manualBarcode = barcode != null && !barcode.isBlank();
        return manualBarcode != generateBarcode;
    }
}
