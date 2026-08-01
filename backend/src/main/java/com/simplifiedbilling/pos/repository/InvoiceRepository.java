package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);

    Optional<Invoice> findByInvoiceNumberIgnoreCase(String invoiceNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findDetailedByIdForUpdate(@Param("id") String id);
}
