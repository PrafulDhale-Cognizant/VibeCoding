package com.simplifiedbilling.purchasing.repository;

import com.simplifiedbilling.purchasing.domain.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, String> {

    @EntityGraph(attributePaths = "payableBalance")
    @Query("select s from Supplier s where s.id = :id")
    Optional<Supplier> findDetailedById(@Param("id") String id);

    @EntityGraph(attributePaths = "payableBalance")
    @Query(value = """
            select s from Supplier s join s.payableBalance b
            where (:pattern is null or lower(s.name) like :pattern or s.phone like :pattern
                   or lower(coalesce(s.gstin, '')) like :pattern)
              and (:active is null or s.active = :active)
              and (:balanceStatus = 'ALL'
                   or (:balanceStatus = 'DUE' and b.outstandingAmount > 0)
                   or (:balanceStatus = 'CLEAR' and b.outstandingAmount = 0))
            """,
            countQuery = """
            select count(s) from Supplier s join s.payableBalance b
            where (:pattern is null or lower(s.name) like :pattern or s.phone like :pattern
                   or lower(coalesce(s.gstin, '')) like :pattern)
              and (:active is null or s.active = :active)
              and (:balanceStatus = 'ALL'
                   or (:balanceStatus = 'DUE' and b.outstandingAmount > 0)
                   or (:balanceStatus = 'CLEAR' and b.outstandingAmount = 0))
            """)
    Page<Supplier> search(
            @Param("pattern") String pattern,
            @Param("active") Boolean active,
            @Param("balanceStatus") String balanceStatus,
            Pageable pageable);

    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, String id);
    boolean existsByGstin(String gstin);
    boolean existsByGstinAndIdNot(String gstin, String id);
    long countByActiveTrue();
}
