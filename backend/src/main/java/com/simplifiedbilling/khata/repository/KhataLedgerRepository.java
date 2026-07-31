package com.simplifiedbilling.khata.repository;

import com.simplifiedbilling.khata.domain.KhataLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KhataLedgerRepository extends JpaRepository<KhataLedgerEntry, String> {

    Page<KhataLedgerEntry> findByCustomer_IdOrderByOccurredAtDesc(String customerId, Pageable pageable);

    Optional<KhataLedgerEntry> findByIdempotencyKey(String idempotencyKey);

    boolean existsByInvoiceId(String invoiceId);
}
