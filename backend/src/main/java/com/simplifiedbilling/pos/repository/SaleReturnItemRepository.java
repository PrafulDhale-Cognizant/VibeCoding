package com.simplifiedbilling.pos.repository;

import com.simplifiedbilling.pos.domain.SaleReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem, String> {
    @Query("""
            select coalesce(sum(item.quantity), 0) as quantity,
                   coalesce(sum(item.grossAmount), 0) as grossAmount,
                   coalesce(sum(item.discountAmount), 0) as discountAmount,
                   coalesce(sum(item.taxableAmount), 0) as taxableAmount,
                   coalesce(sum(item.cgstAmount), 0) as cgstAmount,
                   coalesce(sum(item.sgstAmount), 0) as sgstAmount,
                   coalesce(sum(item.igstAmount), 0) as igstAmount,
                   coalesce(sum(item.lineTotal), 0) as lineTotal
              from SaleReturnItem item where item.invoiceItem.id = :invoiceItemId
            """)
    SaleReturnLineTotals returnedTotals(@Param("invoiceItemId") String invoiceItemId);
}
