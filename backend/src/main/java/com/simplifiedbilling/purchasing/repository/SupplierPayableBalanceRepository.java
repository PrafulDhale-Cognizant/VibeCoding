package com.simplifiedbilling.purchasing.repository;

import com.simplifiedbilling.purchasing.domain.SupplierPayableBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SupplierPayableBalanceRepository
        extends JpaRepository<SupplierPayableBalance, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SupplierPayableBalance b join fetch b.supplier where b.supplierId = :supplierId")
    Optional<SupplierPayableBalance> findBySupplierIdForUpdate(@Param("supplierId") String supplierId);

    @Query("select coalesce(sum(b.outstandingAmount), 0) from SupplierPayableBalance b")
    BigDecimal totalOutstanding();

    long countByOutstandingAmountGreaterThan(BigDecimal amount);
}
