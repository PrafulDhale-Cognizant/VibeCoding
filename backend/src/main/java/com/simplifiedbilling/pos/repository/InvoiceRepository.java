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

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);

    Optional<Invoice> findByInvoiceNumberIgnoreCase(String invoiceNumber);

    @Query(value = "select distinct i from Invoice i left join i.payments p "
            + "where :query = '' or lower(i.invoiceNumber) like concat('%', :query, '%') "
            + "or lower(coalesce(p.customerName, '')) like concat('%', :query, '%') "
            + "or coalesce(p.customerPhone, '') like concat('%', :query, '%')",
            countQuery = "select count(distinct i) from Invoice i left join i.payments p "
                    + "where :query = '' or lower(i.invoiceNumber) like concat('%', :query, '%') "
                    + "or lower(coalesce(p.customerName, '')) like concat('%', :query, '%') "
                    + "or coalesce(p.customerPhone, '') like concat('%', :query, '%')")
    Page<Invoice> searchInvoices(@Param("query") String query, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findDetailedByIdForUpdate(@Param("id") String id);
}
