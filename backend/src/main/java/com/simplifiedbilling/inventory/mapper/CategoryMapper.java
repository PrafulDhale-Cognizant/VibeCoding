package com.simplifiedbilling.inventory.mapper;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.dto.CategoryResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.isActive(),
                category.getVersion(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
