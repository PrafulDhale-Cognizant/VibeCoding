package com.simplifiedbilling.purchasing.repository;

import com.simplifiedbilling.purchasing.domain.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, String> {

    @EntityGraph(attributePaths = {"supplier", "items"})
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findDetailedById(@Param("id") String id);

    @EntityGraph(attributePaths = {"supplier", "items"})
    @Query("select p from Purchase p where p.idempotencyKey = :key")
    Optional<Purchase> findDetailedByIdempotencyKey(@Param("key") String key);

    @Query(value = """
            select p from Purchase p
            where (:supplierId is null or p.supplier.id = :supplierId)
              and (:fromDate is null or p.invoiceDate >= :fromDate)
              and (:toDate is null or p.invoiceDate <= :toDate)
              and (:pattern is null or lower(p.purchaseNumber) like :pattern
                   or lower(coalesce(p.supplierInvoiceNumber, '')) like :pattern
                   or lower(p.supplierName) like :pattern)
            """,
            countQuery = """
            select count(p) from Purchase p
            where (:supplierId is null or p.supplier.id = :supplierId)
              and (:fromDate is null or p.invoiceDate >= :fromDate)
              and (:toDate is null or p.invoiceDate <= :toDate)
              and (:pattern is null or lower(p.purchaseNumber) like :pattern
                   or lower(coalesce(p.supplierInvoiceNumber, '')) like :pattern
                   or lower(p.supplierName) like :pattern)
            """)
    Page<Purchase> search(
            @Param("supplierId") String supplierId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("pattern") String pattern,
            Pageable pageable);

    boolean existsBySupplier_IdAndSupplierInvoiceNumber(String supplierId, String supplierInvoiceNumber);
}
