package com.simplifiedbilling.inventory.repository;

import com.simplifiedbilling.inventory.domain.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, String> {

    boolean existsByValue(String value);

    boolean existsByValueAndProduct_IdNot(String value, String productId);
}
