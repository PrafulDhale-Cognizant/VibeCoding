package com.simplifiedbilling.purchasing.repository;

import com.simplifiedbilling.purchasing.domain.SupplierLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SupplierLedgerRepository extends JpaRepository<SupplierLedgerEntry, String> {

    Page<SupplierLedgerEntry> findBySupplier_IdOrderByOccurredAtDesc(
            String supplierId, Pageable pageable);

    Optional<SupplierLedgerEntry> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select e.supplier.id as supplierId, coalesce(sum(e.amount), 0) as amount
            from SupplierLedgerEntry e
            where e.entryType = com.simplifiedbilling.purchasing.domain.SupplierLedgerEntryType.PAYMENT
              and e.occurredAt >= :startInclusive and e.occurredAt < :endExclusive
            group by e.supplier.id
            """)
    List<SupplierAmountAggregate> paymentsBySupplier(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);
}
