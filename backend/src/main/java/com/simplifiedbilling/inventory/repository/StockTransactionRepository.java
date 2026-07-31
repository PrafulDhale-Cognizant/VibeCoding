package com.simplifiedbilling.inventory.repository;

import com.simplifiedbilling.inventory.domain.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, String> {

    Page<StockTransaction> findByProduct_IdOrderByOccurredAtDesc(String productId, Pageable pageable);
}
