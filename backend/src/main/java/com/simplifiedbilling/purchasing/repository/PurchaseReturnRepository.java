package com.simplifiedbilling.purchasing.repository;

import com.simplifiedbilling.purchasing.domain.PurchaseReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, String> {

    @EntityGraph(attributePaths = {"purchase", "supplier", "items", "items.purchaseItem"})
    @Query("select r from PurchaseReturn r where r.id = :id")
    Optional<PurchaseReturn> findDetailedById(@Param("id") String id);

    @EntityGraph(attributePaths = {"purchase", "supplier", "items", "items.purchaseItem"})
    @Query("select r from PurchaseReturn r where r.idempotencyKey = :key")
    Optional<PurchaseReturn> findDetailedByIdempotencyKey(@Param("key") String key);

    @EntityGraph(attributePaths = {"purchase", "supplier"})
    @Query(value = """
            select r from PurchaseReturn r
            where (:supplierId is null or r.supplier.id = :supplierId)
              and (:purchaseId is null or r.purchase.id = :purchaseId)
              and (:fromDate is null or r.returnDate >= :fromDate)
              and (:toDate is null or r.returnDate <= :toDate)
              and (:pattern is null or lower(r.returnNumber) like :pattern
                   or lower(r.purchase.purchaseNumber) like :pattern
                   or lower(r.supplierName) like :pattern)
            """,
            countQuery = """
            select count(r) from PurchaseReturn r
            where (:supplierId is null or r.supplier.id = :supplierId)
              and (:purchaseId is null or r.purchase.id = :purchaseId)
              and (:fromDate is null or r.returnDate >= :fromDate)
              and (:toDate is null or r.returnDate <= :toDate)
              and (:pattern is null or lower(r.returnNumber) like :pattern
                   or lower(r.purchase.purchaseNumber) like :pattern
                   or lower(r.supplierName) like :pattern)
            """)
    Page<PurchaseReturn> search(
            @Param("supplierId") String supplierId,
            @Param("purchaseId") String purchaseId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("pattern") String pattern,
            Pageable pageable);

    @Query("""
            select r.supplier.id as supplierId, coalesce(sum(r.totalAmount), 0) as amount
            from PurchaseReturn r
            where r.returnDate between :fromDate and :toDate
            group by r.supplier.id
            """)
    List<SupplierAmountAggregate> totalBySupplier(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
