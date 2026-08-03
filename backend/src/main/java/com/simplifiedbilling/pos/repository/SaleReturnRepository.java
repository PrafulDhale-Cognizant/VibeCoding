package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.domain.SaleReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

import java.util.Optional;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, String> {
    @Query("select r from SaleReturn r where r.id = :id")
    Optional<SaleReturn> findDetailedById(@Param("id") String id);
    Optional<SaleReturn> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(r.totalAmount), 0) from SaleReturn r where r.invoice.id = :invoiceId")
    BigDecimal returnedTotal(@Param("invoiceId") String invoiceId);

    @EntityGraph(attributePaths = "invoice")
    @Query(value = """
            select r from SaleReturn r
             where (:pattern is null
                or lower(r.returnNumber) like :pattern
                or lower(r.invoice.invoiceNumber) like :pattern
                or lower(r.reason) like :pattern)
            """,
            countQuery = """
            select count(r) from SaleReturn r
             where (:pattern is null
                or lower(r.returnNumber) like :pattern
                or lower(r.invoice.invoiceNumber) like :pattern
                or lower(r.reason) like :pattern)
            """)
    Page<SaleReturn> search(@Param("pattern") String pattern, Pageable pageable);
}
