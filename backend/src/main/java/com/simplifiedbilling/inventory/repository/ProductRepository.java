package com.simplifiedbilling.inventory.repository;

import com.simplifiedbilling.inventory.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    @EntityGraph(attributePaths = {"category", "barcode", "stockBalance"})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findDetailedById(@Param("id") String id);

    @EntityGraph(attributePaths = {"category", "barcode", "stockBalance"})
    @Query("select p from Product p join p.barcode b where b.value = :barcode")
    Optional<Product> findDetailedByBarcode(@Param("barcode") String barcode);

    @EntityGraph(attributePaths = {"category", "barcode", "stockBalance"})
    @Query(value = """
            select p
            from Product p
            join p.stockBalance balance
            left join p.barcode barcode
            where (
                :searchPattern is null
                or lower(p.name) like :searchPattern
                or lower(p.receiptName) like :searchPattern
                or lower(p.sku) like :searchPattern
                or lower(barcode.value) like :searchPattern
            )
            and (:categoryId is null or p.category.id = :categoryId)
            and (:active is null or p.active = :active)
            and (
                :stockStatus = 'ALL'
                or (:stockStatus = 'IN_STOCK' and balance.quantity > p.minimumStockLevel)
                or (:stockStatus = 'LOW_STOCK' and balance.quantity > 0 and balance.quantity <= p.minimumStockLevel)
                or (:stockStatus = 'OUT_OF_STOCK' and balance.quantity = 0)
            )
            """,
            countQuery = """
            select count(p)
            from Product p
            join p.stockBalance balance
            left join p.barcode barcode
            where (
                :searchPattern is null
                or lower(p.name) like :searchPattern
                or lower(p.receiptName) like :searchPattern
                or lower(p.sku) like :searchPattern
                or lower(barcode.value) like :searchPattern
            )
            and (:categoryId is null or p.category.id = :categoryId)
            and (:active is null or p.active = :active)
            and (
                :stockStatus = 'ALL'
                or (:stockStatus = 'IN_STOCK' and balance.quantity > p.minimumStockLevel)
                or (:stockStatus = 'LOW_STOCK' and balance.quantity > 0 and balance.quantity <= p.minimumStockLevel)
                or (:stockStatus = 'OUT_OF_STOCK' and balance.quantity = 0)
            )
            """)
    Page<Product> search(
            @Param("searchPattern") String searchPattern,
            @Param("categoryId") String categoryId,
            @Param("active") Boolean active,
            @Param("stockStatus") String stockStatus,
            Pageable pageable);

    boolean existsByCategoryIdAndActiveTrue(String categoryId);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, String id);
}
