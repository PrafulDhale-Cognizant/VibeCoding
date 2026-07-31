package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.dto.CategoryCreateRequest;
import com.simplifiedbilling.inventory.dto.CategoryResponse;
import com.simplifiedbilling.inventory.dto.CategoryUpdateRequest;
import com.simplifiedbilling.inventory.mapper.CategoryMapper;
import com.simplifiedbilling.inventory.repository.CategoryRepository;
import com.simplifiedbilling.inventory.repository.ProductRepository;
import com.simplifiedbilling.inventory.service.CategoryService;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class DefaultCategoryService implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public DefaultCategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            CategoryMapper categoryMapper,
            AuditWriter auditWriter,
            Clock clock) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryMapper = categoryMapper;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(boolean includeInactive) {
        List<Category> categories = includeInactive
                ? categoryRepository.findAllByOrderByNameAsc()
                : categoryRepository.findAllByActiveTrueOrderByNameAsc();
        return categories.stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(String actorUserId, CategoryCreateRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw duplicateName();
        }
        Category category = Category.create(name, Instant.now(clock));
        categoryRepository.saveAndFlush(category);
        auditWriter.write(
                actorUserId,
                "CATEGORY_CREATED",
                "CATEGORY",
                category.getId(),
                Map.of("name", category.getName()));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(
            String actorUserId,
            String categoryId,
            CategoryUpdateRequest request) {

        Category category = requireCategory(categoryId);
        if (category.getVersion() != request.version()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "STALE_CATEGORY_VERSION",
                    "The category has changed. Refresh and try again.");
        }
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, categoryId)) {
            throw duplicateName();
        }
        if (category.isActive()
                && !request.active()
                && productRepository.existsByCategoryIdAndActiveTrue(categoryId)) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "CATEGORY_IN_USE",
                    "Deactivate or move active products before deactivating this category.");
        }

        category.update(name, request.active(), Instant.now(clock));
        categoryRepository.flush();
        auditWriter.write(
                actorUserId,
                "CATEGORY_UPDATED",
                "CATEGORY",
                category.getId(),
                Map.of("name", category.getName(), "active", category.isActive()));
        return categoryMapper.toResponse(category);
    }

    private Category requireCategory(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "CATEGORY_NOT_FOUND",
                        "The category does not exist."));
    }

    private ApplicationException duplicateName() {
        return new ApplicationException(
                HttpStatus.CONFLICT,
                "CATEGORY_NAME_EXISTS",
                "A category with this name already exists.");
    }
}
