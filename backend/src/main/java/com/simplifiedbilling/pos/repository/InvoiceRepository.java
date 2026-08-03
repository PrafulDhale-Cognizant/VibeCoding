package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.math.BigDecimal;
import java.time.Instant;

import com.simplifiedbilling.pos.domain.InvoiceStatus;
import com.simplifiedbilling.pos.domain.PaymentMode;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);

    Optional<Invoice> findByInvoiceNumberIgnoreCase(String invoiceNumber);

    @Query(value = "select distinct i from Invoice i left join i.payments p "
            + "where (:query = '' or lower(i.invoiceNumber) like concat('%', :query, '%') "
            + "or lower(coalesce(p.customerName, '')) like concat('%', :query, '%') "
            + "or coalesce(p.customerPhone, '') like concat('%', :query, '%')) "
            + "and (:status is null or i.status = :status) "
            + "and (:paymentMode is null or p.mode = :paymentMode) "
            + "and (:fromTime is null or i.completedAt >= :fromTime) "
            + "and (:toTime is null or i.completedAt < :toTime) "
            + "and (:minAmount is null or i.totalAmount >= :minAmount) "
            + "and (:maxAmount is null or i.totalAmount <= :maxAmount)",
            countQuery = "select count(distinct i) from Invoice i left join i.payments p "
                    + "where (:query = '' or lower(i.invoiceNumber) like concat('%', :query, '%') "
                    + "or lower(coalesce(p.customerName, '')) like concat('%', :query, '%') "
                    + "or coalesce(p.customerPhone, '') like concat('%', :query, '%')) "
                    + "and (:status is null or i.status = :status) "
                    + "and (:paymentMode is null or p.mode = :paymentMode) "
                    + "and (:fromTime is null or i.completedAt >= :fromTime) "
                    + "and (:toTime is null or i.completedAt < :toTime) "
                    + "and (:minAmount is null or i.totalAmount >= :minAmount) "
                    + "and (:maxAmount is null or i.totalAmount <= :maxAmount)")
    Page<Invoice> searchInvoices(
            @Param("query") String query,
            @Param("status") InvoiceStatus status,
            @Param("paymentMode") PaymentMode paymentMode,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findDetailedByIdForUpdate(@Param("id") String id);
}
