package com.simplifiedbilling.khata.repository;

import com.simplifiedbilling.khata.domain.CustomerCreditBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface CustomerCreditBalanceRepository extends JpaRepository<CustomerCreditBalance, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from CustomerCreditBalance b join fetch b.customer where b.customerId = :customerId")
    Optional<CustomerCreditBalance> findByCustomerIdForUpdate(@Param("customerId") String customerId);

    @Query("select coalesce(sum(b.outstandingAmount), 0) from CustomerCreditBalance b")
    BigDecimal totalOutstanding();

    long countByOutstandingAmountGreaterThan(BigDecimal amount);
}
