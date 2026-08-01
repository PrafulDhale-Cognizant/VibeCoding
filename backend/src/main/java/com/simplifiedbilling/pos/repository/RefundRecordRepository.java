package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.domain.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, String> {
    @Query("select coalesce(sum(r.amount), 0) from RefundRecord r where r.saleReturn.invoice.id = :invoiceId and r.mode = com.simplifiedbilling.pos.domain.PaymentMode.UDHAAR")
    BigDecimal refundedUdhaar(@Param("invoiceId") String invoiceId);
}
