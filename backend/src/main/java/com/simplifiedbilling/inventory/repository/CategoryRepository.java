package com.simplifiedbilling.inventory.repository;

import com.simplifiedbilling.inventory.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findAllByOrderByNameAsc();

    List<Category> findAllByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);
}
