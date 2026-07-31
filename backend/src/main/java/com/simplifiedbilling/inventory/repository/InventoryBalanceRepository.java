package com.simplifiedbilling.inventory.repository;

import com.simplifiedbilling.inventory.domain.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b join fetch b.product where b.productId = :productId")
    Optional<InventoryBalance> findByProductIdForUpdate(@Param("productId") String productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBalance b
            join fetch b.product p
            join fetch p.barcode
            where b.productId in :productIds
            order by b.productId
            """)
    List<InventoryBalance> findAllByProductIdsForUpdate(
            @Param("productIds") Collection<String> productIds);
}
