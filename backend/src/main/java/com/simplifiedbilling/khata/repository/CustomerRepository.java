package com.simplifiedbilling.khata.repository;

import com.simplifiedbilling.khata.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    @EntityGraph(attributePaths = "creditBalance")
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findDetailedById(@Param("id") String id);

    @EntityGraph(attributePaths = "creditBalance")
    @Query(value = """
            select c from Customer c join c.creditBalance b
            where (:pattern is null or lower(c.name) like :pattern or c.phone like :pattern)
              and (:active is null or c.active = :active)
              and (:balanceStatus = 'ALL'
                   or (:balanceStatus = 'DUE' and b.outstandingAmount > 0)
                   or (:balanceStatus = 'CLEAR' and b.outstandingAmount = 0))
            """,
            countQuery = """
            select count(c) from Customer c join c.creditBalance b
            where (:pattern is null or lower(c.name) like :pattern or c.phone like :pattern)
              and (:active is null or c.active = :active)
              and (:balanceStatus = 'ALL'
                   or (:balanceStatus = 'DUE' and b.outstandingAmount > 0)
                   or (:balanceStatus = 'CLEAR' and b.outstandingAmount = 0))
            """)
    Page<Customer> search(
            @Param("pattern") String pattern,
            @Param("active") Boolean active,
            @Param("balanceStatus") String balanceStatus,
            Pageable pageable);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, String id);

    long countByActiveTrue();
}
