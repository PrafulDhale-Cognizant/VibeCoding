package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);
}
