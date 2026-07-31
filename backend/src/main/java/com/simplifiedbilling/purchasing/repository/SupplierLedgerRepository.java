package com.simplifiedbilling.purchasing.repository;

import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierLedgerRepository extends JpaRepository<SupplierLedgerEntry, String> {

    Page<SupplierLedgerEntry> findBySupplier_IdOrderByOccurredAtDesc(
            String supplierId, Pageable pageable);

    Optional<SupplierLedgerEntry> findByIdempotencyKey(String idempotencyKey);
}
