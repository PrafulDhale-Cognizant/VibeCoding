package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.dto.CategoryCreateRequest;
import com.simplifiedbilling.inventory.dto.CategoryResponse;
import com.simplifiedbilling.inventory.dto.CategoryUpdateRequest;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> listCategories(boolean includeInactive);

    CategoryResponse createCategory(String actorUserId, CategoryCreateRequest request);

    CategoryResponse updateCategory(
            String actorUserId,
            String categoryId,
            CategoryUpdateRequest request);
}
